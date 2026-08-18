package net.lumalyte.lg.interaction.menus.guild

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

    override fun open() {
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        if (ranks.isEmpty()) {
            player.sendMessage("§cThis guild has no ranks configured.")
            menuNavigator.goBack()
            return
        }

        val rows = (ranks.size + 8) / 9 + 1 // 1 row for header nav
        val height = rows.coerceIn(3, 6)
        val gui = ChestGui(height, "§6Ranks — ${guild.name}")
        val pane = StaticPane(0, 0, 9, height)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }
        gui.addPane(pane)

        val maxSlots = (height - 1) * 9 // Reserve last row for back button
        var slot = 0
        for (rank in ranks) {
            if (slot >= maxSlots) {
                // Overflow notice
                val overflowItem = ItemStack.of(Material.PAPER)
                    .name("§e... and ${ranks.size - slot} more ranks")
                pane.addItem(GuiItem(overflowItem), 4, height - 2)
                break
            }
            val displayIcon = try {
                rank.icon?.let { Material.valueOf(it.uppercase()) } ?: Material.NAME_TAG
            } catch (_: Exception) { Material.NAME_TAG }

            val permCount = rank.permissions.size
            val item = ItemStack.of(displayIcon)
                .name("§e${rank.name}")
                .lore("§7Priority: §f${rank.priority}")
                .lore("§7Permissions: §f$permCount")
                .lore("")
            if (permCount > 0) {
                val perms = rank.permissions.take(8).joinToString("§7, §f") { it.name }
                item.lore("§7Includes: §f$perms")
                if (rank.permissions.size > 8) {
                    item.lore("§7...and ${rank.permissions.size - 8} more")
                }
            } else {
                item.lore("§7No special permissions")
            }
            pane.addItem(GuiItem(item), slot % 9, slot / 9)
            slot++
        }

        // Back button in bottom-right
        val backItem = ItemStack.of(Material.ARROW)
            .name("§e⬅ BACK")
            .lore("§7Return to rank management")
        pane.addItem(GuiItem(backItem) { menuNavigator.goBack() }, 8, height - 1)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}