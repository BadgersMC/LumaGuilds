package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.FurnaceGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimIcon
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimIconResult
import net.lumalyte.lg.domain.entities.Claim
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

class ClaimIconMenu(private val player: Player, private val menuNavigator: MenuNavigator,
                    private val claim: Claim?): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val updateClaimIcon: UpdateClaimIcon by inject()

    override fun open() {
        if (claim == null) {
            player.sendMessage(lang.msg("menu.common.feedback.no_claim"))
            return
        }

        val playerId = player.uniqueId
        val gui = FurnaceGui(lang.legacy("menu.icon.title"))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        val fuelPane = StaticPane(0, 0, 1, 1)

        // Add info paper menu item
        val paperItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.icon.item.info.name"))
            .lore(lang.legacy("menu.icon.item.info.lore"))
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

            inputPane.addItem(GuiItem(ItemStack.of(cursor)), 0, 0)
            gui.update()
            thread(start = true) {
                Thread.sleep(1)
                player.setItemOnCursor(temp)
            }
        }
        gui.ingredientComponent.addPane(inputPane)

        // Add confirm menu item
        val outputPane = StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack.of(Material.NETHER_STAR)
            .name(lang.legacy("menu.common.item.confirm.name"))
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
