package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
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

class HomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val homeName: String
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.home_access.permission_denied"))
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val home = guildService.getHome(guild.id, homeName)
        if (home == null) {
            player.sendMessage(lang.msg("menu.home_access.missing", "home" to homeName))
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4, lang.legacy("menu.home_access.title", "home" to homeName)))
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(pane)

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val ownerRank = rankService.getHighestRank(guild.id)
        val allowed = home.allowedRankIds.toMutableSet()

        ranks.take(27).forEachIndexed { idx, r ->
            val row = idx / 9
            val col = idx % 9
            val isOwner = r.id == ownerRank?.id
            val on = isOwner || r.id in allowed
            val item = ItemStack.of(
                when {
                    isOwner -> Material.NETHER_STAR
                    on -> Material.LIME_DYE
                    else -> Material.GRAY_DYE
                }
            ).name(
                if (on) lang.legacy("menu.home_access.rank.allowed", "rank" to r.name)
                else lang.legacy("menu.home_access.rank.denied", "rank" to r.name)
            )
                .lore(lang.legacy("menu.home_access.rank.priority", "priority" to r.priority))
                .lore(lang.legacy("menu.common.blank"))
                .lore(
                    when {
                        isOwner -> lang.legacy("menu.home_access.rank.owner")
                        on -> lang.legacy("menu.home_access.rank.revoke")
                        else -> lang.legacy("menu.home_access.rank.grant")
                    }
                )
            pane.addItem(GuiItem(item) {
                if (isOwner) return@GuiItem
                if (r.id in allowed) allowed.remove(r.id) else allowed.add(r.id)
                guildService.setHomeAllowedRanks(guild.id, homeName, allowed.toSet(), player.uniqueId)
                open()
            }, col, row)
        }

        val backItem = ItemStack.of(Material.ARROW).name(lang.legacy("menu.home_access.back"))
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 8, 3)

        gui.show(player)
    }
}
