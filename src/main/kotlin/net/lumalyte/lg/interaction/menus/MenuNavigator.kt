package net.lumalyte.lg.interaction.menus

import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class MenuNavigator(private val player: Player, private val messageService: MessageService) {
    private val menuStack = ArrayDeque<Menu>()

    init {
        // Register this navigator instance for the player
        activeNavigators[player.uniqueId] = this
    }

    /**
     * Opens the provided menu for the target player.
     *
     * Opening menus this way stores it to the navigation stack, which is
     * required for menus to understand how to go back when prompted for it.
     *
     * @param menu The menu to open.
     */
    fun openMenu(menu: Menu) {
        menuStack.addFirst(menu)
        menu.open()
    }

    /**
     * Opens the previous menu in the navigation stack.
     *
     * This closes the currently displayed menu and moves the menu display back
     * a step in the stack when prompted for it.
     */
    fun goBack() {
        navigateBack()
    }

    /**
     * Opens the previous menu in the navigation stack while also passing data.
     *
     * This closes the currently displayed menu and moves the menu display back
     * a step in the stack when prompted for it, it also accepts a data type of
     * any that can be used by the previously opened menu (e.g. passing search
     * data)
     *
     * @param data Data type of any to pass to the previous menu.
     */
    fun goBackWithData(data: Any?) {
        navigateBack(data)
    }

    /**
     * Clears the entire menu stack.
     *
     * This can be used to ensure that going back will instead close out of the
     * menu.
     */
    fun clearMenuStack() {
        menuStack.clear()
    }

    private fun navigateBack(data: Any? = null) {
        if (menuStack.isNotEmpty()) {
            menuStack.removeFirst()
            if (menuStack.isNotEmpty()) {
                menuStack.first().passData(data)
                menuStack.first().open()
            } else {
                // Unregister when menu stack is empty
                activeNavigators.remove(player.uniqueId)
                player.closeInventory()
            }
        }
    }

    companion object {
        // Registry to track active navigators by player UUID
        private val activeNavigators = ConcurrentHashMap<java.util.UUID, MenuNavigator>()

        /**
         * Clears cached menus for a specific player.
         * This removes all menu instances from the navigation stack to prevent
         * memory leaks and continued exploit attempts after menu closure.
         *
         * @param player The player whose menu cache should be cleared
         */
        fun clearMenuCache(player: Player) {
            val navigator = activeNavigators[player.uniqueId]
            if (navigator != null) {
                navigator.clearMenuStack()
                activeNavigators.remove(player.uniqueId)
            }
        }
    }
}
