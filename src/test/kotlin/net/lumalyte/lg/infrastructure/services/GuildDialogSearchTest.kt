package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SEARCH_CATEGORY = "quests"
private const val ALL_CATEGORY = "all"
private const val QUERY_SIZE = 64
private const val OVERSIZED_QUERY = 100

internal class GuildDialogSearchTest {
    @Test
    fun validatesBoundedSearch() {
        var submitted: Pair<String, String>? = null
        val search =
            GuildDialogSearch(
                Component.text("Search"),
                "",
                Component.text("Category"),
                ALL_CATEGORY,
                listOf(ALL_CATEGORY to Component.text("All"), SEARCH_CATEGORY to Component.text("Quests")),
                Component.text("Update results"),
            ) { query, category -> submitted = query to category }
        assertFalse(search.accept("name", "forged"))
        assertEquals(null, submitted)
        assertTrue(search.accept("  " + "a".repeat(OVERSIZED_QUERY) + "  ", SEARCH_CATEGORY))
        assertEquals("a".repeat(QUERY_SIZE) to SEARCH_CATEGORY, submitted)
        assertTrue(search.accept(" ", ALL_CATEGORY))
        assertEquals("" to ALL_CATEGORY, submitted)
    }
}
