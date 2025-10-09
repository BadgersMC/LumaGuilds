package net.lumalyte.lg.utils

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerDropItemEvent
import org.slf4j.LoggerFactory

object AntiDupeUtil {

    private val logger = LoggerFactory.getLogger(AntiDupeUtil::class.java)

    fun protect(gui: ChestGui) {
        // Block drag operations completely
        gui.setOnGlobalDrag { event ->
            event.isCancelled = true
            handleExploitDetected(event.whoClicked as Player, "DRAG_OPERATION")
        }

        // Block specific bottom inventory interactions (player's inventory)
        // Only called ONCE to prevent event handler conflicts
        gui.setOnBottomClick { event ->
            val player = event.whoClicked as Player

            // Block specific click types
            when (event.click) {
                ClickType.MIDDLE, ClickType.RIGHT,
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                    event.isCancelled = true
                    handleExploitDetected(player, event.click.name)
                }
                else -> {
                    // Check for number key presses (1-9)
                    if (event.hotbarButton != -1) {
                        event.isCancelled = true
                        handleExploitDetected(player, "NUMBER_KEY_${event.hotbarButton + 1}")
                    }
                    // Check for window border clicks (outside inventory)
                    else if (event.slot == -1) {
                        event.isCancelled = true
                        handleExploitDetected(player, "WINDOW_BORDER_CLICK")
                    }
                }
            }
        }
    }


    // Separate method to handle drop events (called from external listener)
    fun handleDropEvent(event: PlayerDropItemEvent, player: Player) {
        if (player.openInventory.topInventory.holder != null) {
            event.isCancelled = true
            handleExploitDetected(player, "DROP_KEY_Q")
        }
    }

    private fun handleExploitDetected(player: Player, action: String) {
        // Log the unauthorized attempt
        logger.warn("Blocked menu exploit attempt: ${player.name} tried $action")

        // Close the menu immediately
        player.closeInventory()

        // Clear any cached menu state for this player to prevent memory leaks
        // and continued exploit attempts
        net.lumalyte.lg.interaction.menus.MenuNavigator.clearMenuCache(player)
    }
}
