package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberService
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

class GuildLeaveConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, "§e§lLeave ${guild.name}?"))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }
        gui.addPane(pane)

        // Info item
        val infoItem = ItemStack.of(Material.OAK_DOOR)
            .name("§e🚪 Leave Guild")
            .lore("§7Guild: §f${guild.name}")
            .lore("§7")
            .lore("§eYou will lose access to:")
            .lore("§7- Guild bank and vault")
            .lore("§7- Guild homes")
            .lore("§7- Guild chat channels")
            .lore("§7- All rank permissions")
        pane.addItem(GuiItem(infoItem), 4, 0)

        // Confirm leave
        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name("§c§lCONFIRM LEAVE")
            .lore("§7Leave this guild permanently")
        pane.addItem(GuiItem(confirmItem) {
            val success = memberService.removeMember(player.uniqueId, guild.id, player.uniqueId)
            if (success) {
                player.sendMessage("§eYou have left §f${guild.name}§e.")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
                player.closeInventory()
            } else {
                player.sendMessage("§cFailed to leave guild. You may be the owner — transfer ownership first.")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }, 3, 2)

        // Cancel
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