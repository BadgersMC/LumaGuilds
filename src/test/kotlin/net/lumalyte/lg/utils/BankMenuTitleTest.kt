package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BankMenuTitleTest {
    @Test
    fun `themed bank titles provide readable default for uncolored legacy text`() {
        for (theme in GuiTheme.SELECTABLE.filter { it.hasBackground }) {
            assertEquals("§6Guild Bank - Example", BankMenuTitle.forTheme(theme, "Guild Bank - Example"))
            assertEquals("§6§lGuild Bank", BankMenuTitle.forTheme(theme, "§lGuild Bank"))
        }
    }

    @Test
    fun `explicit title colors and vanilla formatting are preserved`() {
        for (theme in GuiTheme.SELECTABLE) {
            for (title in listOf("§fBank - Example", "§6§lBank", "§l§cBank", "§x§1§2§3§4§5§6Bank", "§rBank")) {
                assertEquals(title, BankMenuTitle.forTheme(theme, title))
            }
        }
        assertEquals("Guild Bank - Example", BankMenuTitle.forTheme(GuiTheme.VANILLA, "Guild Bank - Example"))
        assertEquals("", BankMenuTitle.forTheme(GuiTheme.ENTHUSIA, ""))
    }
}
