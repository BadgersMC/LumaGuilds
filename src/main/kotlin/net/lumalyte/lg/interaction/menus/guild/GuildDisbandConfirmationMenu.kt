package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildDisbandConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()

    override fun open() {
        val gui = ChestGui(3, "§c§lDisband ${guild.name}?")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }
        gui.addPane(pane)

        // Info item
        val infoItem = ItemStack.of(Material.BARRIER)
            .name("§c⚠ Disband Guild")
            .lore("§7Guild: §f${guild.name}")
            .lore("§7Level: §f${guild.level}")
            .lore("§7")
            .lore("§cThis action cannot be undone!")
            .lore("§7All vault items will be lost.")
        pane.addItem(GuiItem(infoItem), 4, 0)

        // Confirm button
        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name("§c§lCONFIRM DISBAND")
            .lore("§7Click to disband this guild permanently")
        pane.addItem(GuiItem(confirmItem) {
            val success = guildService.disbandGuild(guild.id, player.uniqueId)
            if (success) {
                player.sendMessage("§c❌ Guild §f${guild.name} §chas been disbanded.")
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
                player.closeInventory()
                menuNavigator.clearMenuStack()
            } else {
                player.sendMessage("§cFailed to disband guild. You may not be the owner.")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }, 3, 2)

        // Cancel button
        val cancelItem = ItemStack.of(Material.GREEN_WOOL)
            .name("§a§lCANCEL")
            .lore("§7Return to guild settings")
        pane.addItem(GuiItem(cancelItem) {
            menuNavigator.goBack()
        }, 5, 2)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}