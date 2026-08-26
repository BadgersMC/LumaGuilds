package net.lumalyte.lg.interaction.menus.guild

import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test

class JoinRequirementsNavigationTest {

    @Test
    fun `return to LFG opens the browser menu`() {
        val menuNavigator = mockk<MenuNavigator>(relaxed = true)
        returnToLfgBrowser(menuNavigator)

        verify(exactly = 1) { menuNavigator.goBack() }
        verify(exactly = 0) { menuNavigator.openMenu(any()) }
    }
}
