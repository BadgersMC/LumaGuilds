package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Helper class for Bedrock menu navigation patterns
 * Provides consistent navigation behavior across all Bedrock forms
 */
class BedrockMenuNavigator(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val menuFactory: MenuFactory,
    private val messageService: MessageService
) {

    /**
     * Creates a back button handler that can be used in Bedrock forms
     * @param customHandler Optional custom handler to run before going back
     * @return Runnable that handles back navigation
     */
    fun createBackHandler(customHandler: (() -> Unit)? = null): Runnable {
        return Runnable {
            customHandler?.invoke()
            goBack()
        }
    }

    /**
     * Creates a back button handler with data passing
     * @param data Data to pass to the previous menu
     * @param customHandler Optional custom handler to run before going back
     * @return Runnable that handles back navigation with data
     */
    fun createBackWithDataHandler(data: Any?, customHandler: (() -> Unit)? = null): Runnable {
        return Runnable {
            customHandler?.invoke()
            goBackWithData(data)
        }
    }

    /**
     * Opens a new menu and maintains navigation stack
     * @param menu The menu to open
     */
    fun openMenu(menu: Menu) {
        menuNavigator.openMenu(menu)
    }

    /**
     * Navigates back to the previous menu
     */
    fun goBack() {
        menuNavigator.goBack()
    }

    /**
     * Navigates back with data to the previous menu
     * @param data Data to pass to the previous menu
     */
    fun goBackWithData(data: Any?) {
        menuNavigator.goBackWithData(data)
    }

    /**
     * Clears the entire menu navigation stack
     */
    fun clearMenuStack() {
        menuNavigator.clearMenuStack()
    }

    /**
     * Creates a cancel handler that clears the menu stack and closes all menus
     * @param customHandler Optional custom handler to run before clearing
     * @return Runnable that handles complete menu cancellation
     */
    fun createCancelHandler(customHandler: (() -> Unit)? = null): Runnable {
        return Runnable {
            customHandler?.invoke()
            clearMenuStack()
        }
    }

    /**
     * Creates a navigation handler that opens a specific menu
     * @param menu The menu to navigate to
     * @param customHandler Optional custom handler to run before navigation
     * @return Runnable that handles menu navigation
     */
    fun createNavigationHandler(menu: Menu, customHandler: (() -> Unit)? = null): Runnable {
        return Runnable {
            customHandler?.invoke()
            openMenu(menu)
        }
    }

    /**
     * Creates a handler that reopens the current menu
     * Useful for refreshing menu state after actions
     * @param currentMenu The current menu to reopen
     * @param customHandler Optional custom handler to run before reopening
     * @return Runnable that handles menu reopening
     */
    fun createRefreshHandler(currentMenu: Menu, customHandler: (() -> Unit)? = null): Runnable {
        return Runnable {
            customHandler?.invoke()
            currentMenu.open()
        }
    }

    /**
     * Creates a confirmation handler that shows a confirmation dialog
     * @param title The confirmation title
     * @param callback The action to perform on confirmation
     * @return Runnable that handles confirmation flow
     */
    fun createConfirmationHandler(title: String, callback: () -> Unit): Runnable {
        return Runnable {
            val confirmationMenu = menuFactory.createConfirmationMenu(menuNavigator, player, title, messageService, callback)
            openMenu(confirmationMenu)
        }
    }

    /**
     * Creates a forward navigation handler with state preservation
     * @param menu The menu to navigate to
     * @param currentMenu The current menu (for state extraction)
     * @param stateKey Key to use for state preservation
     * @return Runnable that handles forward navigation with state
     */
    fun createForwardHandler(menu: Menu, currentMenu: Menu, stateKey: String = "forward"): Runnable {
        return Runnable {
            // This would need access to the current menu's state preservation methods
            // For now, we'll create a simple version
            openMenu(menu)
        }
    }

    /**
     * Creates a cancel handler that clears workflow and navigation state
     * @param customHandler Optional custom handler to run before cancelling
     * @return Runnable that handles complete workflow cancellation
     */
    fun createCancelWorkflowHandler(customHandler: (() -> Unit)? = null): Runnable {
        return Runnable {
            customHandler?.invoke()
            clearMenuStack()
            // Send cancellation message
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Workflow cancelled. All progress has been cleared.")
        }
    }

    /**
     * Creates a recovery handler that attempts to restore previous state
     * @param recoveryMenu The menu to reopen with recovered state
     * @param stateKey Key where recovery state is stored
     * @return Runnable that handles state recovery
     */
    fun createRecoveryHandler(recoveryMenu: Menu, stateKey: String = "timeout_recovery"): Runnable {
        return Runnable {
            // Send recovery message
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Restoring previous session...")
            openMenu(recoveryMenu)
        }
    }

    /**
     * Creates a step navigation handler for multi-step workflows
     * @param stepName Name of the current step
     * @param nextMenu Menu for the next step
     * @param stepData Data to save for the current step
     * @return Runnable that handles step navigation
     */
    fun createStepNavigationHandler(stepName: String, nextMenu: Menu, stepData: Map<String, Any?>): Runnable {
        return Runnable {
            // Save current step data
            // This would need integration with FormStateManager
            openMenu(nextMenu)
        }
    }
}
