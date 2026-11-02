package net.lumalyte.lg.utils

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Background glyph font handling for themed menu titles. */
@Suppress("MagicNumber")
internal class MenuTitleGlyphsTest {
    private val nexoFont = Key.key("nexo", "default")
    private val lookup: (String) -> Component? = { id ->
        if (id == "guild_bg_enthusia_6_row") Component.text("ꁅ").font(nexoFont) else null
    }

    @Test fun retiredPlateNeverChangesHeadingCursorAcrossThemesAndRows() {
        for (theme in GuiTheme.SELECTABLE.filter { it.hasBackground }) {
            for (rows in 1..6) {
                val suffix = "${theme.name.lowercase()}_${rows}_row"
                val raw = Component.text(MenuTitleBuilder.build(theme, rows, "Statistics"))
                val background = Component.text("\uE900\uF801").font(nexoFont)
                val requests = mutableListOf<String>()
                val fixed = MenuTitleGlyphs.withGlyphFonts(raw) { id ->
                    requests += id
                    when (id) {
                        "guild_bg_$suffix" -> background
                        "guild_inventory_label_$suffix" -> Component.text("\uE901").font(nexoFont)
                        else -> null
                    }
                }
                assertEquals("<shift:-8>\uE900\uF801<shift:-162>Statistics",
                    PlainTextComponentSerializer.plainText().serialize(fixed), "$theme/$rows")
                assertFalse(requests.any { it.startsWith("guild_inventory_label_") }, "$theme/$rows")
                assertEquals(fixed, MenuTitleGlyphs.withGlyphFonts(fixed) { background }, "$theme/$rows")
            }
        }
    }

    @Test fun alreadyResolvedNexoBackgroundAndSpacingRemainUntouched() {
        val background = Component.text("\uE900\uF801").font(nexoFont)
        val title = Component.text("<shift:-8>").append(background)
            .append(Component.text("<shift:-162>Statistics"))
        val fixed = MenuTitleGlyphs.withGlyphFonts(title) { id ->
            when (id) {
                "guild_bg_enthusia_3_row" -> background
                "guild_inventory_label_enthusia_3_row" -> Component.text("\uE901").font(nexoFont)
                else -> null
            }
        }
        assertEquals(title, fixed)
    }

    private fun fonts(c: Component): List<Pair<String, Key?>> {
        val out = mutableListOf<Pair<String, Key?>>()
        fun walk(x: Component, inherited: Key?) {
            val f = x.style().font() ?: inherited
            if (x is TextComponent && x.content().isNotEmpty()) out += x.content() to f
            x.children().forEach { walk(it, f) }
        }
        walk(c, null)
        return out
    }

    /** Background glyph tag becomes the glyph with its nexo font. */
    @Test
    fun backgroundTagGetsNexoFont() {
        val raw = Component.text(MenuTitleBuilder.build(GuiTheme.ENTHUSIA, 6, "Guild Settings"))
        val fixed = MenuTitleGlyphs.withGlyphFonts(raw, lookup)
        assertTrue(fonts(fixed).any { it.first == "ꁅ" && it.second == nexoFont }, fonts(fixed).toString())
        val plain = PlainTextComponentSerializer.plainText().serialize(fixed)
        assertFalse(plain.contains("<glyph:"))
        assertTrue(plain.contains("<shift:-8>") && plain.contains("<shift:-162>Guild Settings"), plain)
    }

    /** Unknown glyphs keep their tag so nexo can still try. */
    @Test
    fun unknownGlyphKeepsTag() {
        val raw = Component.text(MenuTitleBuilder.build(GuiTheme.VOIDLIGHT, 3, "X"))
        assertEquals(raw, MenuTitleGlyphs.withGlyphFonts(raw, lookup))
    }

    /** Titles without a guild background are untouched. */
    @Test
    fun plainTitlesUntouched() {
        val raw = Component.text("Crate Rewards")
        assertFalse(MenuTitleGlyphs.hasBackgroundGlyph(raw))
        assertEquals(raw, MenuTitleGlyphs.withGlyphFonts(raw, lookup))
    }
}
