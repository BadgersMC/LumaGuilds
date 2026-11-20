package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.AllyHomeRankPicks
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.createHead
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * One ally's page. Row 1 (REQ-182): which of the ally's ranks may use our allied
 * homes. Row 3 (REQ-183): which of OUR ranks may use the ally's allied homes; the
 * owner always can, so the owner rank is not listed. Row 4: the ally's members;
 * clicking one blocks or unblocks them from our allied homes.
 */
class AllyHomeVisitorMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val host: Guild,
    private val allyId: UUID,
    private val allyName: String,
    private val page: Int = 0,
) : Menu, KoinComponent {
    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, host.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.ally_home_access.permission_denied"))
            menuNavigator.goBack()
            return
        }
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(host.guiTheme, ROWS,
            lang.guiTitle("menu.ally_home_access.visitor.title", "guild" to allyName)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(buildPane())
        gui.show(player)
    }

    /** The page's buttons; separate from [open] so it can be checked without a client. */
    internal fun buildPane(): StaticPane {
        val access = guildService.allyHomeAccess(host.id)
        val picks = access.visitorRanks[allyId]
        val ranks = rankService.listRanks(allyId).sortedBy { it.priority }
        val allRanks = ranks.map { it.id }.toSet()
        val members = memberService.getGuildMembers(allyId)
            .map { Bukkit.getOfflinePlayer(it.playerId) }
            .sortedBy { (it.name ?: it.uniqueId.toString()).lowercase() }
        val pages = ((members.size + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)

        val pane = StaticPane(0, 0, 9, ROWS)

        val header = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.ally_home_access.visitor.ranks_header"))
            .lore(lang.gui("menu.ally_home_access.visitor.ranks_hint"))
        pane.addItem(GuiItem(header), 0, 0)
        val reset = ItemStack.of(Material.LIME_STAINED_GLASS_PANE)
            .name(lang.gui("menu.ally_home_access.visitor.reset.name"))
            .lore(lang.gui("menu.ally_home_access.visitor.reset.description"))
        pane.addItem(GuiItem(reset) { save(guildService.setAllyHomeVisitorRanks(host.id, allyId, null, player.uniqueId)) }, 8, 0)

        ranks.take(9).forEachIndexed { idx, rank ->
            val on = AllyHomeRankPicks.isAllowed(picks, rank.id)
            val item = NexoItemProvider.getItemStackOrFallback(if (on) "lg_toggle_on" else "lg_toggle_off") {
                ItemStack.of(if (on) Material.LIME_DYE else Material.GRAY_DYE)
            }
                .name(
                    if (on) lang.gui("menu.ally_home_access.visitor.rank.allowed", "rank" to rank.name)
                    else lang.gui("menu.ally_home_access.visitor.rank.denied", "rank" to rank.name)
                )
                .lore(lang.gui("menu.ally_home_access.visitor.rank.toggle"))
            pane.addItem(GuiItem(item) {
                val next = AllyHomeRankPicks.toggle(picks, allRanks, rank.id)
                save(guildService.setAllyHomeVisitorRanks(host.id, allyId, next, player.uniqueId))
            }, idx, 1)
        }

        addOutboundRow(pane)

        members.drop(current * PER_PAGE).take(PER_PAGE).forEachIndexed { idx, target ->
            val blocked = target.uniqueId in access.blockedPlayers
            val name = target.name ?: target.uniqueId.toString()
            val head = createHead(target)
                .name(
                    if (blocked) lang.gui("menu.ally_home_access.visitor.member.blocked", "player" to name)
                    else lang.gui("menu.ally_home_access.visitor.member.can", "player" to name)
                )
                .lore(lang.gui("menu.ally_home_access.visitor.member.toggle"))
            pane.addItem(GuiItem(head) {
                save(guildService.setAllyHomeBlocked(host.id, target.uniqueId, !blocked, player.uniqueId))
            }, idx % 9, MEMBER_ROW + idx / 9)
        }

        if (current > 0) {
            pane.addItem(GuiItem(ItemStack.of(Material.ARROW).name(lang.gui("menu.ally_home_access.visitor.previous"))) {
                menuNavigator.openMenu(AllyHomeVisitorMenu(menuNavigator, player, host, allyId, allyName, current - 1))
            }, 0, ROWS - 1)
        }
        if (current < pages - 1) {
            pane.addItem(GuiItem(ItemStack.of(Material.ARROW).name(lang.gui("menu.ally_home_access.visitor.next"))) {
                menuNavigator.openMenu(AllyHomeVisitorMenu(menuNavigator, player, host, allyId, allyName, current + 1))
            }, 2, ROWS - 1)
        }
        val back = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.ally_home_access.visitor.back"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 4, ROWS - 1)
        return pane
    }

    /** REQ-183: our own ranks for visiting this ally; no pick means rank permissions decide. */
    private fun addOutboundRow(pane: StaticPane) {
        val pick = guildService.allyHomeOutboundRanks(host.id)[allyId]
        val owner = rankService.getHighestRank(host.id)?.id
        val ours = rankService.listRanks(host.id).filter { it.id != owner }.sortedBy { it.priority }
        val header = ItemStack.of(Material.COMPASS)
            .name(lang.gui("menu.ally_home_access.visitor.ours_header", "guild" to allyName))
            .lore(
                if (pick == null) lang.gui("menu.ally_home_access.visitor.ours_default")
                else lang.gui("menu.ally_home_access.visitor.ours_custom")
            )
            .lore(lang.gui("menu.ally_home_access.visitor.ours_owner"))
        pane.addItem(GuiItem(header), 0, OURS_HEADER_ROW)
        val reset = ItemStack.of(Material.LIME_STAINED_GLASS_PANE)
            .name(lang.gui("menu.ally_home_access.visitor.ours_reset.name"))
            .lore(lang.gui("menu.ally_home_access.visitor.ours_reset.description"))
        pane.addItem(GuiItem(reset) {
            save(guildService.setAllyHomeOutboundRanks(host.id, allyId, null, player.uniqueId))
        }, 8, OURS_HEADER_ROW)
        val defaults = ours.filter { RankPermission.USE_ALLY_HOMES in it.permissions }.map { it.id }.toSet()
        ours.take(9).forEachIndexed { idx, rank ->
            val on = pick?.contains(rank.id) ?: (rank.id in defaults)
            val item = NexoItemProvider.getItemStackOrFallback(if (on) "lg_toggle_on" else "lg_toggle_off") {
                ItemStack.of(if (on) Material.LIME_DYE else Material.GRAY_DYE)
            }
                .name(
                    if (on) lang.gui("menu.ally_home_access.visitor.rank.allowed", "rank" to rank.name)
                    else lang.gui("menu.ally_home_access.visitor.rank.denied", "rank" to rank.name)
                )
                .lore(lang.gui("menu.ally_home_access.visitor.ours_toggle", "guild" to allyName))
            pane.addItem(GuiItem(item) {
                val base = pick ?: defaults
                val next = if (rank.id in base) base - rank.id else base + rank.id
                save(guildService.setAllyHomeOutboundRanks(host.id, allyId, next, player.uniqueId))
            }, idx, OURS_ROW)
        }
    }

    private fun save(ok: Boolean) {
        if (!ok) player.sendMessage(lang.msg("menu.ally_home_access.save_failed"))
        open()
    }

    private companion object {
        const val ROWS = 6
        const val OURS_HEADER_ROW = 2
        const val OURS_ROW = 3
        const val MEMBER_ROW = 4
        const val PER_PAGE = 9
    }
}
