package net.lumalyte.lg.interaction.menus

import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuNavigatorTest {
    private class ParentMenu(val opened: () -> Unit) : Menu { override fun open() = opened() }

    @Test fun `named parent return discards descendants retains state and invalidates async work`() {
        val navigator = MenuNavigator(mockk(relaxed = true))
        val visited = mutableListOf<String>()
        var page = 3
        val root = object : Menu { override fun open() { visited += "root" } }
        val parent = ParentMenu { visited += "parent-$page" }
        navigator.openMenu(root)
        navigator.openMenu(parent)
        repeat(2) { navigator.openMenu(mockk(relaxed = true)) }
        val token = navigator.currentNavigationToken()
        navigator.returnTo(ParentMenu::class.java) { error("Existing parent must be reused") }
        assertFalse(navigator.isNavigationCurrent(token))
        kotlin.test.assertEquals("parent-3", visited.last())
        page = 4
        navigator.returnTo(ParentMenu::class.java) { error("Current parent must be reused") }
        kotlin.test.assertEquals("parent-4", visited.last())
        navigator.goBack()
        kotlin.test.assertEquals("root", visited.last())
    }

    @Test fun `direct parent return replaces child and final Back closes instead of reopening it`() {
        val player = mockk<Player>(relaxed = true)
        val navigator = MenuNavigator(player)
        val child = mockk<Menu>(relaxed = true)
        var opened = 0
        navigator.openMenu(child)
        navigator.returnTo(ParentMenu::class.java) { ParentMenu { opened++ } }
        val presentation = net.lumalyte.lg.infrastructure.services.RecruitmentPresentation
        io.mockk.mockkObject(presentation)
        try {
            io.mockk.every { presentation.supportsDialog(player) } returns false
            navigator.goBack()
        } finally {
            io.mockk.unmockkObject(presentation)
        }
        kotlin.test.assertEquals(1, opened)
        io.mockk.verify(exactly = 1) { child.open() }
        io.mockk.verify { player.closeInventory() }
    }

    @Test fun `missing parent fallback preserves earlier root and empty stack opens fallback`() {
        val navigator = MenuNavigator(mockk(relaxed = true))
        var rootOpens = 0
        navigator.openMenu(object : Menu { override fun open() { rootOpens++ } })
        navigator.openMenu(mockk(relaxed = true))
        navigator.returnTo(ParentMenu::class.java) { ParentMenu {} }
        navigator.goBack()
        kotlin.test.assertEquals(2, rootOpens)
        navigator.clearMenuStack()
        var opens = 0
        navigator.returnTo(ParentMenu::class.java) { ParentMenu { opens++ } }
        kotlin.test.assertEquals(1, opens)
    }

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
