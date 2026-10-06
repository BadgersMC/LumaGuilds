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
@Suppress("MagicNumber")
class MenuTitleBuilderTest {

    // ---------------------------------------------------------------
    // 1. Row-count → glyph selection
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
        "ENTHUSIA,   1, guild_bg_enthusia_1_row",
        "ENTHUSIA,   3, guild_bg_enthusia_3_row",
        "ENTHUSIA,   4, guild_bg_enthusia_4_row",
        "ENTHUSIA,   5, guild_bg_enthusia_5_row",
        "ENTHUSIA,   6, guild_bg_enthusia_6_row",
        "FROSTBOUND,   1, guild_bg_frostbound_1_row",
        "FROSTBOUND,   3, guild_bg_frostbound_3_row",
        "FROSTBOUND,   4, guild_bg_frostbound_4_row",
        "FROSTBOUND,   5, guild_bg_frostbound_5_row",
        "FROSTBOUND,   6, guild_bg_frostbound_6_row",
        "VERDANT,   1, guild_bg_verdant_1_row",
        "VERDANT,   3, guild_bg_verdant_3_row",
        "VERDANT,   4, guild_bg_verdant_4_row",
        "VERDANT,   5, guild_bg_verdant_5_row",
        "VERDANT,   6, guild_bg_verdant_6_row",
        "VOIDLIGHT,   1, guild_bg_voidlight_1_row",
        "VOIDLIGHT,   3, guild_bg_voidlight_3_row",
        "VOIDLIGHT,   4, guild_bg_voidlight_4_row",
        "VOIDLIGHT,   5, guild_bg_voidlight_5_row",
        "VOIDLIGHT,   6, guild_bg_voidlight_6_row",
        "OBSIDIAN,   1, guild_bg_obsidian_1_row",
        "OBSIDIAN,   3, guild_bg_obsidian_3_row",
        "OBSIDIAN,   4, guild_bg_obsidian_4_row",
        "OBSIDIAN,   5, guild_bg_obsidian_5_row",
        "OBSIDIAN,   6, guild_bg_obsidian_6_row",
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
        val title = MenuTitleBuilder.build(GuiTheme.ENTHUSIA, 3)
        assertTrue(
            title.startsWith("<shift:-9>"),
            "Expected title '$title' to start with '<shift:-9>'"
        )
    }

    @Test
    fun `prefix is identical across themes and row counts`() {
        val titles =
            GuiTheme.entries.filter { it.hasBackground }.flatMap { theme ->
                listOf(1, 3, 4, 5, 6).map { rows ->
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
        for (rows in listOf(1, 3, 4, 5, 6)) {
            val title = MenuTitleBuilder.build(rows = rows)
            assertTrue(
                title.contains("_${rows}_row"),
                "Expected title '$title' for $rows-row menu to contain '_${rows}_row'"
            )
        }
    }

    // ---------------------------------------------------------------
    // 4. Default parameters use ENTHUSIA and the provided row count
    // ---------------------------------------------------------------

    /** Default theme is ENTHUSIA. */
    @Test
    fun defaultThemeIsEnthusia() {
        val title = MenuTitleBuilder.build(rows = 3)
        assertTrue(title.contains("guild_bg_enthusia_3_row"))
    }

    // ---------------------------------------------------------------
    // 5. No-title path produces background-only string
    // ---------------------------------------------------------------

    @Test
    fun `no stray content after the glyph tag when no title`() {
        val title = MenuTitleBuilder.build(GuiTheme.FROSTBOUND, 6)
        assertTrue(
            title.endsWith("<glyph:guild_bg_frostbound_6_row>"),
            "Expected title to end with glyph tag, got: '$title'",
        )
    }

    // ---------------------------------------------------------------
    // 6. Visible title text after rewind
    // ---------------------------------------------------------------

    @Test
    fun `title text appears after rewind shift`() {
        val title = MenuTitleBuilder.build(GuiTheme.ENTHUSIA, 3, "⚔ My Guild")
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
        val title = MenuTitleBuilder.build(GuiTheme.VOIDLIGHT, 4, "Info")
        val glyphIdx = title.indexOf("<glyph:guild_bg_voidlight_4_row>")
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
        val title = MenuTitleBuilder.build(GuiTheme.FROSTBOUND, 6, "§6Info - $guildName §8• Page $page/$total")
        assertTrue(title.contains("§6Info - Enthusia §8• Page 1/3"),
            "Dynamic title not preserved, got: '$title'")
    }

    // ---------------------------------------------------------------
    // 7. Representative 3-row and 6-row menus
    // ---------------------------------------------------------------

    @Test
    fun `three row menu with title`() {
        val title = MenuTitleBuilder.build(GuiTheme.ENTHUSIA, 3, "⚔ Dashboard")
        assertTrue(title.startsWith("<shift:-9>"), "3-row must start with shift:-9")
        assertTrue(title.contains("<glyph:guild_bg_enthusia_3_row>"), "3-row must use 3_row glyph")
        assertTrue(title.contains("⚔ Dashboard"), "Title text must be present")
    }

    @Test
    fun `six row menu with title`() {
        val title = MenuTitleBuilder.build(GuiTheme.VERDANT, 6, "Member Management")
        assertTrue(title.startsWith("<shift:-9>"), "6-row must start with shift:-9")
        assertTrue(title.contains("<glyph:guild_bg_verdant_6_row>"), "6-row must use 6_row glyph")
        assertTrue(title.contains("Member Management"), "Title text must be present")
    }

    // ---------------------------------------------------------------
    // Vanilla style: no custom background at all
    // ---------------------------------------------------------------

    /** Vanilla theme has no background glyph or pixel shifts. */
    @Test
    fun vanillaHasNoGlyphOrShifts() {
        assertFalse(GuiTheme.VANILLA.hasBackground)
        for (rows in listOf(1, 3, 4, 5, 6)) {
            val title = MenuTitleBuilder.build(GuiTheme.VANILLA, rows, "§fGuild Actions")
            assertFalse(title.contains("<glyph:"), "Vanilla title must not use a glyph: '$title'")
            assertFalse(title.contains("<shift:"), "Vanilla title must not shift: '$title'")
        }
    }

    /** Vanilla theme keeps the title text in the default dark chest colour. */
    @Test
    fun vanillaKeepsDefaultTitleColour() {
        assertEquals("§rGuild Actions", MenuTitleBuilder.build(GuiTheme.VANILLA, 3, "§fGuild Actions"))
        assertEquals("§r⚔ War - §cRed", MenuTitleBuilder.build(GuiTheme.VANILLA, 3, "§f⚔ War - §cRed"))
        assertEquals("", MenuTitleBuilder.build(GuiTheme.VANILLA, 3))
    }

    /** Every other theme still draws a background. */
    @Test
    fun otherThemesDrawBackground() {
        GuiTheme.entries.filter { it != GuiTheme.VANILLA }.forEach { assertTrue(it.hasBackground, it.name) }
    }

    // ---------------------------------------------------------------
    // Only the Enthusia-era styles are offered; older stored themes render as Enthusia
    // ---------------------------------------------------------------

    /** Picker offers only the Enthusia styles and Vanilla. */
    @Test
    fun pickerOffersEnthusiaStyles() {
        val expected =
            listOf(
                GuiTheme.ENTHUSIA,
                GuiTheme.FROSTBOUND,
                GuiTheme.VERDANT,
                GuiTheme.VOIDLIGHT,
                GuiTheme.HALLOWEEN,
                GuiTheme.CHRISTMAS,
                GuiTheme.OBSIDIAN,
                GuiTheme.VANILLA,
            )
        assertEquals(expected, GuiTheme.SELECTABLE)
        assertEquals(GuiTheme.ENTHUSIA, GuiTheme.DEFAULT)
    }

    /** Older stored themes render with the Enthusia background. */
    @Test
    fun legacyThemesRenderAsEnthusia() {
        val legacyThemes =
            listOf(
                GuiTheme.NEUTRAL,
                GuiTheme.EMBERSTONE,
                GuiTheme.CARVED_SLATE,
                GuiTheme.MOSSBOUND,
                GuiTheme.LAVENDER_HALL,
                GuiTheme.IRON_ROSE,
            )
        for (legacy in legacyThemes) {
            assertEquals(GuiTheme.ENTHUSIA, legacy.resolved(), legacy.name)
            assertTrue(MenuTitleBuilder.build(legacy, 4, "X").contains("<glyph:guild_bg_enthusia_4_row>"), legacy.name)
        }
        assertEquals(GuiTheme.ENTHUSIA, GuiTheme.fromKey("no_such_theme"))
        assertEquals(GuiTheme.OBSIDIAN, GuiTheme.fromKey("obsidian"))
    }
}
