package net.lumalyte.lg.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.FurnaceGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimIcon
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimIconResult
import net.lumalyte.lg.domain.entities.Claim
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
import kotlin.concurrent.thread
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class ClaimIconMenu(private val player: Player, private val menuNavigator: MenuNavigator,
                    private val claim: Claim?, private val messageService: MessageService): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val updateClaimIcon: UpdateClaimIcon by inject()

    override fun open() {
        if (claim == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Error: No claim available")
            return
        }

        val playerId = player.uniqueId
        val gui = FurnaceGui(localizationProvider.get(playerId, LocalizationKeys.MENU_ICON_TITLE))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        val fuelPane = StaticPane(0, 0, 1, 1)

        // Add info paper menu item
        val paperItem = ItemStack(Material.PAPER)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_ICON_ITEM_INFO_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_ICON_ITEM_INFO_LORE))
        val guiIconEditorItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
        fuelPane.addItem(guiIconEditorItem, 0, 0)
        gui.fuelComponent.addPane(fuelPane)

        // Allow item to be placed in slot
        val inputPane = StaticPane(0, 0, 1, 1)
        inputPane.setOnClick { guiEvent ->
            guiEvent.isCancelled = true
            val temp = guiEvent.cursor
            val cursor = guiEvent.cursor?.type ?: Material.AIR

            if (cursor == Material.AIR) {
                inputPane.removeItem(0, 0)
                gui.update()
                return@setOnClick
            }

            inputPane.addItem(GuiItem(ItemStack(cursor)), 0, 0)
            gui.update()
            thread(start = true) {
                Thread.sleep(1)
                player.setItemOnCursor(temp)
            }
        }
        gui.ingredientComponent.addPane(inputPane)

        // Add confirm menu item
        val outputPane = StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_COMMON_ITEM_CONFIRM_NAME))
        val confirmGuiItem = GuiItem(confirmItem) { guiEvent ->
            guiEvent.isCancelled = true
            val newIcon = gui.ingredientComponent.getItem(0, 0)

            // Set icon if item in slot
            if (newIcon != null) {
                when (val result = updateClaimIcon.execute(claim.id, newIcon.type.name)) {
                    is UpdateClaimIconResult.Success -> menuNavigator.goBackWithData(result.claim)
                    else -> menuNavigator.goBack()
                }
                return@GuiItem
            }

            // Go back to edit menu if no item in slot
            menuNavigator.goBack()
        }
        outputPane.addItem(confirmGuiItem, 0, 0)
        gui.outputComponent.addPane(outputPane)
        gui.show(player)
    }
}
