package net.lumalyte.lg.interaction.menus.common

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.domain.values.LocalizationKeys
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
import kotlin.getValue
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class ConfirmationMenu(val menuNavigator: MenuNavigator, val player: Player, val title: String,
                       val callbackAction: (messageService: MessageService) -> Unit): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val messageService: MessageService by inject()

    override fun open() {
        val playerId = player.uniqueId
        val gui = ChestGui(1, title)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        val pane = StaticPane(0, 0, 9, 1)

        // Add no menu item
        val noItem = ItemStack(Material.RED_CONCRETE)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CONFIRMATION_ITEM_NO_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_CONFIRMATION_ITEM_NO_LORE))

        val guiNoItem = GuiItem(noItem) { guiEvent ->
            menuNavigator.goBack()
        }
        pane.addItem(guiNoItem, 3, 0)

        // Add yes menu item
        val yesItem = ItemStack(Material.GREEN_CONCRETE)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CONFIRMATION_ITEM_YES_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_CONFIRMATION_ITEM_YES_LORE))
        val guiYesItem = GuiItem(yesItem) { guiEvent ->
            callbackAction(messageService)
            menuNavigator.goBack()
        }
        pane.addItem(guiYesItem, 5, 0)

        // Add the pane to the GUI
        gui.addPane(pane)
        gui.show(player)
    }
}
