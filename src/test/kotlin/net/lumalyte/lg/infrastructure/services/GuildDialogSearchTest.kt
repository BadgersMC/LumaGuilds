package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GuildDialogSearchTest {
    @Test
    fun validatesAndBoundsSubmittedSearch() {
        var submitted: Pair<String, String>? = null
        val search = GuildDialogSearch(
            Component.text("Search"),
            "",
            Component.text("Category"),
            "all",
            listOf("all" to Component.text("All"), "quests" to Component.text("Quests")),
            Component.text("Update results"),
        ) { query, category -> submitted = query to category }
        assertFalse(search.accept("name", "forged"))
        assertEquals(null, submitted)
        assertTrue(search.accept("  " + "a".repeat(100) + "  ", "quests"))
        assertEquals("a".repeat(64) to "quests", submitted)
        assertTrue(search.accept(" ", "all"))
        assertEquals("" to "all", submitted)
    }
}
