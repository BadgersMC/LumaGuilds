package net.lumalyte.lg.interaction.menus

import org.bukkit.entity.Player
import net.lumalyte.lg.infrastructure.services.GuildMenuLifecycle

/** Public navigation contract used by the existing Java and Bedrock menu constructors. */
@Suppress("LibraryEntitiesShouldNotBePublic") // Preserve the public menu-constructor contract.
class MenuNavigator(private val player: Player) {
    private val menuStack = ArrayDeque<Menu>()
    private val navigationGeneration = GuildMenuLifecycle.generation(player)

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
     * Returns to a named parent without putting a duplicate parent above its child.
     * Reuses the nearest parent instance (including its page/filter state). Direct
     * entries replace the current screen with the fallback, so Back cannot revisit it.
     * Only explicit parent-return actions use this; forward opens remain unchanged.
     */
    fun returnTo(parentType: Class<out Menu>, fallback: () -> Menu) {
        navigationGeneration.incrementAndGet()
        restoreParent(parentType, fallback).open()
    }

    /**
     * Returns to a named parent with updated child data while retaining the parent's
     * other state. Data is delivered before opening either the existing parent or
     * its direct-entry fallback. Descendants cannot be revisited by another Back.
     */
    fun returnToWithData(parentType: Class<out Menu>, data: Any?, fallback: () -> Menu) {
        navigationGeneration.incrementAndGet()
        val parent = restoreParent(parentType, fallback)
        parent.passData(data)
        parent.open()
    }

    private fun restoreParent(parentType: Class<out Menu>, fallback: () -> Menu): Menu {
        val parent = menuStack.firstOrNull { parentType.isInstance(it) }
        if (parent != null) {
            while (menuStack.first() !== parent) menuStack.removeFirst()
            return parent
        }
        val replacement = fallback()
        if (menuStack.isNotEmpty()) menuStack.removeFirst()
        menuStack.addFirst(replacement)
        return replacement
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

    /** Read-only exit-label context; direct command entry has no parent to restore. */
    fun hasParent(): Boolean = menuStack.size > 1

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
                if (net.lumalyte.lg.infrastructure.services.RecruitmentPresentation.supportsDialog(player)) {
                    player.closeDialog()
                }
                player.closeInventory()
            }
        }
    }

}
