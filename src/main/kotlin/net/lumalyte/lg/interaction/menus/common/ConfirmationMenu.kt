package net.lumalyte.lg.interaction.menus.common

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.HopperGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
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

class ConfirmationMenu(val menuNavigator: MenuNavigator, val player: Player, val title: String,
                       val callbackAction: () -> Unit): Menu, KoinComponent {
    private val lang: LangService by inject()

    override fun open() {
        val gui = HopperGui(title)
        val pane = StaticPane(1, 0, 3, 1)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.slotsComponent.addPane(pane)
        val playerId = player.uniqueId

        // Add no menu item
        val noItem = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.confirmation.item.no.name"))
            .lore(lang.gui("menu.confirmation.item.no.lore"))

        val guiNoItem = GuiItem(noItem) { guiEvent ->
            menuNavigator.goBack()
        }
        pane.addItem(guiNoItem, 0, 0)

        // Add yes menu item
        val yesItem = ItemStack.of(Material.GREEN_CONCRETE)
            .name(lang.gui("menu.confirmation.item.yes.name"))
            .lore(lang.gui("menu.confirmation.item.yes.lore"))
        val guiYesItem = GuiItem(yesItem) { guiEvent ->
            callbackAction()
            menuNavigator.goBack()
        }
        pane.addItem(guiYesItem, 2, 0)

        gui.show(player)
    }
}
