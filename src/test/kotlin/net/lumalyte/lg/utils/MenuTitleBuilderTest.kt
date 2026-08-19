package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `positioning prefix is always shift 20`() {
        val title = MenuTitleBuilder.build(GuiTheme.NEUTRAL, 3)
        assertTrue(
            title.startsWith("<shift:20>"),
            "Expected title '$title' to start with '<shift:20>'"
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
    // 5. No stray shift characters after the glyph
    // ---------------------------------------------------------------

    @Test
    fun `no content after the glyph tag`() {
        val title = MenuTitleBuilder.build(GuiTheme.EMBERSTONE, 6)
        assertTrue(title.endsWith("<glyph:guild_bg_emberstone_6_row>"), 
            "Expected title to end with the glyph tag, got: '$title'")
    }
}