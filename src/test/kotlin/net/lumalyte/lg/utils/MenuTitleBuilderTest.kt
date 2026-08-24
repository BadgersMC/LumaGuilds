package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Tests for [MenuTitleBuilder].
 *
 * Covers:
 * - Every (theme, rows) combination produces the expected glyph name
 * - Positioning prefix is deterministic and independent of slots/contents
 * - The prefix is identical for every theme and row count
 * - The glyph name correctly reflects the row count
 * - Visible title text appears after the rewind shift when supplied
 * - No-title path still produces a clean background-only string
 */
class MenuTitleBuilderTest {

    // ---------------------------------------------------------------
    // 1. Row-count → glyph selection
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
        "NEUTRAL,   3, guild_bg_neutral_3_row",
        "NEUTRAL,   4, guild_bg_neutral_4_row",
        "NEUTRAL,   5, guild_bg_neutral_5_row",
        "NEUTRAL,   6, guild_bg_neutral_6_row",
        "EMBERSTONE,   3, guild_bg_emberstone_3_row",
        "EMBERSTONE,   4, guild_bg_emberstone_4_row",
        "EMBERSTONE,   5, guild_bg_emberstone_5_row",
        "EMBERSTONE,   6, guild_bg_emberstone_6_row",
        "CARVED_SLATE,   3, guild_bg_carved_slate_3_row",
        "CARVED_SLATE,   4, guild_bg_carved_slate_4_row",
        "CARVED_SLATE,   5, guild_bg_carved_slate_5_row",
        "CARVED_SLATE,   6, guild_bg_carved_slate_6_row",
        "MOSSBOUND,   3, guild_bg_mossbound_3_row",
        "MOSSBOUND,   4, guild_bg_mossbound_4_row",
        "MOSSBOUND,   5, guild_bg_mossbound_5_row",
        "MOSSBOUND,   6, guild_bg_mossbound_6_row",
        "LAVENDER_HALL,   3, guild_bg_lavender_hall_3_row",
        "LAVENDER_HALL,   4, guild_bg_lavender_hall_4_row",
        "LAVENDER_HALL,   5, guild_bg_lavender_hall_5_row",
        "LAVENDER_HALL,   6, guild_bg_lavender_hall_6_row",
        "IRON_ROSE,   3, guild_bg_iron_rose_3_row",
        "IRON_ROSE,   4, guild_bg_iron_rose_4_row",
        "IRON_ROSE,   5, guild_bg_iron_rose_5_row",
        "IRON_ROSE,   6, guild_bg_iron_rose_6_row"
    )
    fun `build returns correct glyph name for each theme and row count`(
        themeName: String,
        rows: Int,
        expectedGlyphName: String
    ) {
        val theme = GuiTheme.valueOf(themeName)
        val title = MenuTitleBuilder.build(theme, rows)
        assertTrue(
            title.contains(expectedGlyphName),
            "Expected title '$title' to contain glyph '$expectedGlyphName'"
        )
    }

    // ---------------------------------------------------------------
    // 2. Positioning prefix is deterministic
    // ---------------------------------------------------------------

    @Test
    fun `positioning prefix is always shift -9`() {
        val title = MenuTitleBuilder.build(GuiTheme.NEUTRAL, 3)
        assertTrue(
            title.startsWith("<shift:-9>"),
            "Expected title '$title' to start with '<shift:-9>'"
        )
    }

    @Test
    fun `prefix is identical across themes and row counts`() {
        val titles = GuiTheme.entries.flatMap { theme ->
            (3..6).map { rows ->
                MenuTitleBuilder.build(theme, rows)
                    .substringBefore("<glyph:")
            }
        }
        val first = titles.first()
        titles.forEachIndexed { i, prefix ->
            assertEquals(first, prefix, "Prefix mismatch at index $i: '$prefix' != '$first'")
        }
    }

    // ---------------------------------------------------------------
    // 3. Row count appears in the glyph name
    // ---------------------------------------------------------------

    @Test
    fun `glyph name contains the correct row count`() {
        for (rows in 3..6) {
            val title = MenuTitleBuilder.build(rows = rows)
            assertTrue(
                title.contains("_${rows}_row"),
                "Expected title '$title' for $rows-row menu to contain '_${rows}_row'"
            )
        }
    }

    // ---------------------------------------------------------------
    // 4. Default parameters use NEUTRAL and the provided row count
    // ---------------------------------------------------------------

    @Test
    fun `default theme is NEUTRAL`() {
        val title = MenuTitleBuilder.build(rows = 3)
        assertTrue(title.contains("guild_bg_neutral_3_row"))
    }

    // ---------------------------------------------------------------
    // 5. No-title path produces background-only string
    // ---------------------------------------------------------------

    @Test
    fun `no stray content after the glyph tag when no title`() {
        val title = MenuTitleBuilder.build(GuiTheme.EMBERSTONE, 6)
        assertTrue(title.endsWith("<glyph:guild_bg_emberstone_6_row>"),
            "Expected title to end with glyph tag, got: '$title'")
    }

    // ---------------------------------------------------------------
    // 6. Visible title text after rewind
    // ---------------------------------------------------------------

    @Test
    fun `title text appears after rewind shift`() {
        val title = MenuTitleBuilder.build(GuiTheme.NEUTRAL, 3, "⚔ My Guild")
        val expectedEnd = "<shift:-161>⚔ My Guild"
        assertTrue(
            title.endsWith(expectedEnd),
            "Expected title '$title' to end with '$expectedEnd'"
        )
    }

    @Test
    fun `title is appended without unsupported standard MiniMessage tags`() {
        val title = MenuTitleBuilder.build(rows = 5, title = "Test Menu")
        assertFalse(
            title.contains("<white>"),
            "Expected title '$title' not to contain an unsupported white tag"
        )
    }

    @Test
    fun `background glyph appears before rewind and title`() {
        val title = MenuTitleBuilder.build(GuiTheme.MOSSBOUND, 4, "Info")
        val glyphIdx = title.indexOf("<glyph:guild_bg_mossbound_4_row>")
        val rewindIdx = title.indexOf("<shift:-161>")
        val textIdx = title.indexOf("Info")
        assertTrue(glyphIdx >= 0, "Glyph must be present")
        assertTrue(rewindIdx > glyphIdx, "Rewind shift must come after glyph (got idx $rewindIdx vs $glyphIdx)")
        assertTrue(textIdx > rewindIdx, "Title text must come after rewind shift (got idx $textIdx vs $rewindIdx)")
    }

    @Test
    fun `title is preserved for dynamic content`() {
        val guildName = "Enthusia"
        val page = 1
        val total = 3
        val title = MenuTitleBuilder.build(GuiTheme.EMBERSTONE, 6, "§6Info - ${guildName} §8• Page ${page}/${total}")
        assertTrue(title.contains("§6Info - Enthusia §8• Page 1/3"),
            "Dynamic title not preserved, got: '$title'")
    }

    // ---------------------------------------------------------------
    // 7. Representative 3-row and 6-row menus
    // ---------------------------------------------------------------

    @Test
    fun `three row menu with title`() {
        val title = MenuTitleBuilder.build(GuiTheme.NEUTRAL, 3, "⚔ Dashboard")
        assertTrue(title.startsWith("<shift:-9>"), "3-row must start with shift:-9")
        assertTrue(title.contains("<glyph:guild_bg_neutral_3_row>"), "3-row must use 3_row glyph")
        assertTrue(title.contains("⚔ Dashboard"), "Title text must be present")
    }

    @Test
    fun `six row menu with title`() {
        val title = MenuTitleBuilder.build(GuiTheme.CARVED_SLATE, 6, "Member Management")
        assertTrue(title.startsWith("<shift:-9>"), "6-row must start with shift:-9")
        assertTrue(title.contains("<glyph:guild_bg_carved_slate_6_row>"), "6-row must use 6_row glyph")
        assertTrue(title.contains("Member Management"), "Title text must be present")
    }
}
