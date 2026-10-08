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
