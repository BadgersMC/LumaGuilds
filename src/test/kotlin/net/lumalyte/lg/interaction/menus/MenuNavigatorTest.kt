package net.lumalyte.lg.interaction.menus

import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuNavigatorTest {
    @Test
    fun `navigation changes invalidate previously issued tokens`() {
        val player = mockk<Player>(relaxed = true)
        val navigator = MenuNavigator(player)
        val first = mockk<Menu>(relaxed = true)
        val second = mockk<Menu>(relaxed = true)

        val initial = navigator.currentNavigationToken()
        assertTrue(navigator.isNavigationCurrent(initial))

        navigator.openMenu(first)
        assertFalse(navigator.isNavigationCurrent(initial))
        val firstMenuToken = navigator.currentNavigationToken()

        navigator.openMenu(second)
        assertFalse(navigator.isNavigationCurrent(firstMenuToken))
        val secondMenuToken = navigator.currentNavigationToken()

        navigator.goBack()
        assertFalse(navigator.isNavigationCurrent(secondMenuToken))
        val backToken = navigator.currentNavigationToken()

        navigator.clearMenuStack()
        assertFalse(navigator.isNavigationCurrent(backToken))
        val clearedToken = navigator.currentNavigationToken()

        navigator.invalidateCurrentNavigation()
        assertFalse(navigator.isNavigationCurrent(clearedToken))
    }
}
