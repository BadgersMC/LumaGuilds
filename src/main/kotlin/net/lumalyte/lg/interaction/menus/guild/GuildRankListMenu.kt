package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildRankListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        if (ranks.isEmpty()) {
            player.sendMessage(lang.msg("menu.rank_list.feedback.empty"))
            menuNavigator.goBack()
            return
        }

        val rows = (ranks.size + 8) / 9 + 1 // 1 row for header nav
        val height = rows.coerceIn(3, 6)
        val gui = ChestGui(height, lang.guiTitle("menu.rank_list.title", "guild" to guild.name))
        val pane = StaticPane(0, 0, 9, height)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }
        gui.addPane(pane)

        val totalDataSlots = (height - 1) * 9
        val needsOverflow = ranks.size > totalDataSlots
        var displayed = 0
        var slot = 0
        for (rank in ranks) {
            if (slot >= totalDataSlots) {
                // Overflow: show overflow notice instead of remaining ranks
                val omitted = ranks.size - displayed
                val overflowItem = ItemStack.of(Material.PAPER).name(if (omitted == 1) {
                    lang.gui("menu.rank_list.item.overflow.single")
                } else {
                    lang.gui("menu.rank_list.item.overflow.multiple", "count" to omitted)
                })
                pane.addItem(GuiItem(overflowItem), 4, height - 2)
                break
            }
            // Only skip the reserved slot when overflow is actually needed
            if (needsOverflow && slot == (height - 2) * 9 + 4) slot++
            if (slot >= totalDataSlots) {
                // Overflow after slot skip too
                val omitted = ranks.size - displayed
                val overflowItem = ItemStack.of(Material.PAPER).name(if (omitted == 1) {
                    lang.gui("menu.rank_list.item.overflow.single")
                } else {
                    lang.gui("menu.rank_list.item.overflow.multiple", "count" to omitted)
                })
                pane.addItem(GuiItem(overflowItem), 4, height - 2)
                break
            }
            val displayIcon = try {
                rank.icon?.let {
                    val mat = Material.valueOf(it.uppercase())
                    if (mat.isItem()) mat else Material.NAME_TAG
                } ?: Material.NAME_TAG
            } catch (_: Exception) { Material.NAME_TAG }

            val permCount = rank.permissions.size
            val item = ItemStack.of(displayIcon)
                .name(lang.gui("menu.rank_list.item.rank.name", "rank" to rank.name))
                .lore(lang.gui("menu.rank_list.item.rank.lore.priority", "priority" to rank.priority))
                .lore(lang.gui("menu.rank_list.item.rank.lore.permission_count", "permission_count" to permCount))
                .lore("")
            if (permCount > 0) {
                val perms = rank.permissions.take(8)
                    .joinToString(lang.raw("menu.rank_list.permission_separator")) { it.name }
                item.lore(lang.gui("menu.rank_list.item.rank.lore.includes", "permissions" to perms))
                if (rank.permissions.size > 8) {
                    item.lore(lang.gui("menu.rank_list.item.rank.lore.more", "count" to (rank.permissions.size - 8)))
                }
            } else {
                item.lore(lang.gui("menu.rank_list.item.rank.lore.none"))
            }
            pane.addItem(GuiItem(item), slot % 9, slot / 9)
            displayed++
            slot++
        }

        // Back button in bottom-right
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_list.item.back.name"))
            .lore(lang.gui("menu.rank_list.item.back.lore"))
        pane.addItem(GuiItem(backItem) { menuNavigator.goBack() }, 8, height - 1)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
