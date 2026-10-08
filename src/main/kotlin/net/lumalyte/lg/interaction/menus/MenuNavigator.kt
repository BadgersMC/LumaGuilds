package net.lumalyte.lg.interaction.menus

import org.bukkit.entity.Player
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/** Public navigation contract used by the existing Java and Bedrock menu constructors. */
@Suppress("LibraryEntitiesShouldNotBePublic") // Preserve the public menu-constructor contract.
class MenuNavigator(private val player: Player) {
    private val menuStack = ArrayDeque<Menu>()
    private val navigationGeneration =
        synchronized(GENERATIONS) {
            GENERATIONS.getOrPut(player) { AtomicLong() }
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
        navigationGeneration.incrementAndGet()
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
        navigationGeneration.incrementAndGet()
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
        navigationGeneration.incrementAndGet()
        navigateBack(data)
    }

    /**
     * Clears the entire menu stack.
     *
     * This can be used to ensure that going back will instead close out of the
     * menu.
     */
    fun clearMenuStack() {
        navigationGeneration.incrementAndGet()
        menuStack.clear()
    }

    /**
     * Returns the current navigation generation for guarding asynchronous menu work.
     */
    fun currentNavigationToken(): Long = navigationGeneration.get()

    /**
     * Returns true while [token] still belongs to the player's current navigation state.
     */
    fun isNavigationCurrent(token: Long): Boolean = navigationGeneration.get() == token

    /**
     * Invalidates asynchronous work without changing the menu stack.
     *
     * Used when a player closes a menu directly instead of navigating through this class.
     */
    fun invalidateCurrentNavigation() {
        navigationGeneration.incrementAndGet()
    }

    private fun navigateBack(data: Any? = null) {
        if (menuStack.isNotEmpty()) {
            menuStack.removeFirst()
            if (menuStack.isNotEmpty()) {
                menuStack.first().passData(data)
                menuStack.first().open()
            } else {
                player.closeInventory()
            }
        }
    }

    private companion object {
        // Commands may create separate navigators for the same live player.
        // Weak keys avoid retaining disconnected players after their menus are released.
        val GENERATIONS = WeakHashMap<Player, AtomicLong>()
    }
}
