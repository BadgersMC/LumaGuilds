package net.lumalyte.lg.interaction.menus

import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuNavigatorTest {
    @Test fun `Back reopens the same GUI and dialog instances with retained page state`() {
        val player = mockk<Player>(relaxed = true)
        val navigator = MenuNavigator(player)
        val visited = mutableListOf<String>()
        var page = 2
        val dashboard = object : Menu { override fun open() { visited += "dashboard" } }
        val browser = object : Menu { override fun open() { visited += "browse-$page" } }
        val detail = object : Menu { override fun open() { visited += "detail" } }
        navigator.openMenu(dashboard)
        navigator.openMenu(browser)
        navigator.openMenu(detail)
        navigator.goBack()
        kotlin.test.assertEquals(listOf("dashboard", "browse-2", "detail", "browse-2"), visited)
        page = 3
        navigator.openMenu(detail)
        navigator.goBack()
        kotlin.test.assertEquals("browse-3", visited.last())
        navigator.goBack()
        kotlin.test.assertEquals("dashboard", visited.last())
    }
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
