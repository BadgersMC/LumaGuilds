package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildAdvancedWarManagementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
, private val messageService: MessageService) : Menu, KoinComponent {

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Advanced War Management"))
        val pane = StaticPane(0, 0, 9, 4)

        // War Stats
        val statsItem = ItemStack(Material.BOOK).setAdventureName(player, messageService, "<green>War Statistics")
            .lore(listOf("<gray>View detailed war stats and progress."))
        pane.addItem(GuiItem(statsItem) { _ ->
            openWarStats()
        }, 2, 1)

        // Surrender
        val surrenderItem = ItemStack(Material.WHITE_BANNER).setAdventureName(player, messageService, "<red>Surrender")
            .lore(listOf("<gray>Surrender the current war (requires confirmation)."))
        pane.addItem(GuiItem(surrenderItem) { _ ->
            openSurrenderConfirmation()
        }, 4, 1)

        // Peace Negotiation
        val negotiationItem = ItemStack(Material.PAPER).setAdventureName(player, messageService, "<aqua>Peace Negotiation")
            .lore(listOf("<gray>Initiate peace negotiations with enemies."))
        pane.addItem(GuiItem(negotiationItem) { _ ->
            openPeaceNegotiation()
        }, 6, 1)

        // Back
        val backItem = ItemStack(Material.BARRIER).setAdventureName(player, messageService, "<red>Back").lore(listOf("<gray>Return to previous menu"))
        pane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 3)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openWarStats() {
        // TODO: Implement war stats view - Task 2.3
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>War stats not yet implemented.")
    }

    private fun openSurrenderConfirmation() {
        // TODO: Implement surrender confirmation dialog - Task 2.3
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Surrender not yet implemented.")
    }

    private fun openPeaceNegotiation() {
        // TODO: Implement peace negotiation UI - Task 2.3
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Peace negotiation not yet implemented.")
    }
}
