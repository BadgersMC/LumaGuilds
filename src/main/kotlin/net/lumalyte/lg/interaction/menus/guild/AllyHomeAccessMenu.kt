package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AllyHomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.ally_home_access.permission_denied"))
            returnToHomes()
            return
        }

        val current = guildService.getGuild(guild.id) ?: return
        val allies: List<Pair<java.util.UUID, String>> = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
            .mapNotNull { rel ->
                val otherId = rel.getOtherGuild(guild.id)
                val otherGuild = guildService.getGuild(otherId)
                if (otherGuild != null) otherId to otherGuild.name else null
            }
        val allowed = current.allyHomeAllowedGuilds.toMutableSet()
        val access = guildService.allyHomeAccess(guild.id)

        val gui = ChestGui(ROWS, MenuTitleBuilder.build(guild.guiTheme, ROWS, lang.guiTitle("menu.ally_home_access.title")))
        val pane = StaticPane(0, 0, 9, ROWS)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(pane)

        // REQ-182: rows 0-1 are the allies. Left-click allows or denies; right-click
        // opens that ally's ranks and players.
        allies.take(MAX_ALLIES).forEachIndexed { idx, (allyId, allyName) ->
            val on = allyId in allowed
            val picks = access.visitorRanks[allyId]
            val item = NexoItemProvider.getItemStackOrFallback(
                if (on) "lg_toggle_on" else "lg_toggle_off"
            ) { ItemStack.of(if (on) Material.LIME_DYE else Material.GRAY_DYE) }
                .name(
                    if (on) lang.gui("menu.ally_home_access.ally.allowed", "guild" to allyName)
                    else lang.gui("menu.ally_home_access.ally.denied", "guild" to allyName)
                )
                .lore(
                    if (picks == null) lang.gui("menu.ally_home_access.ally.ranks_all")
                    else lang.gui("menu.ally_home_access.ally.ranks_some", "count" to picks.size)
                )
                .lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.ally_home_access.ally.toggle"))
                .lore(lang.gui("menu.ally_home_access.ally.configure"))
            pane.addItem(GuiItem(item) { event ->
                if (event.isRightClick) {
                    menuNavigator.openMenu(AllyHomeVisitorMenu(menuNavigator, player, guild, allyId, allyName))
                    return@GuiItem
                }
                if (allyId in allowed) allowed.remove(allyId) else allowed.add(allyId)
                guildService.setAllyHomeAllowedGuilds(guild.id, allowed.toSet(), player.uniqueId)
                open()
            }, idx % 9, idx / 9)
        }

        addBlockedRow(pane, access.blockedPlayers)

        val info = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.ally_home_access.info.name"))
            .lore(lang.gui("menu.ally_home_access.info.description"))
            .lore(lang.gui("menu.ally_home_access.info.instructions"))
        pane.addItem(GuiItem(info), 4, ROWS - 1)

        addBackButton(pane)
        gui.show(player)
    }

    /** REQ-182: row 3 lists blocked players; clicking a head unblocks them. */
    private fun addBlockedRow(pane: StaticPane, blocked: Set<java.util.UUID>) {
        val label = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.ally_home_access.blocked.name", "count" to blocked.size))
            .lore(
                if (blocked.isEmpty()) lang.gui("menu.ally_home_access.blocked.none")
                else lang.gui("menu.ally_home_access.blocked.description")
            )
        pane.addItem(GuiItem(label), 0, BLOCKED_ROW)
        blocked.map { org.bukkit.Bukkit.getOfflinePlayer(it) }
            .sortedBy { (it.name ?: it.uniqueId.toString()).lowercase() }
            .take(8)
            .forEachIndexed { idx, target ->
                val head = net.lumalyte.lg.utils.createHead(target)
                    .name(lang.gui("menu.ally_home_access.blocked.player", "player" to (target.name ?: target.uniqueId.toString())))
                    .lore(lang.gui("menu.ally_home_access.blocked.unblock"))
                pane.addItem(GuiItem(head) {
                    if (!guildService.setAllyHomeBlocked(guild.id, target.uniqueId, false, player.uniqueId)) {
                        player.sendMessage(lang.msg("menu.ally_home_access.save_failed"))
                    }
                    open()
                }, idx + 1, BLOCKED_ROW)
            }
    }

    private fun addBackButton(pane: StaticPane) {
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.ally_home_access.back"))
        pane.addItem(GuiItem(backItem) {
            returnToHomes()
        }, 8, ROWS - 1)
    }

    private fun returnToHomes() {
        menuNavigator.returnTo(GuildHomeMenu::class.java) {
            menuFactory.createGuildHomeMenu(menuNavigator, player, guild)
        }
    }

    private companion object {
        const val ROWS = 4
        const val MAX_ALLIES = 18
        const val BLOCKED_ROW = 2
    }
}
