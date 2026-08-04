package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-Kotlin tests for [ColorCodeUtils.emojiToGlyphTag], the guild-emoji →
 * Nexo `<glyph:name>` converter used by %lumaguilds_guild_emoji_minimessage%
 * (Velocitab/NexoProxy-safe) and the `emoji_mm` leaderboard field.
 */
class ColorCodeUtilsEmojiGlyphTest {

    @Test
    fun `converts discord emoji to nexo glyph tag`() {
        assertEquals("<glyph:catsmileysmile>", ColorCodeUtils.emojiToGlyphTag(":catsmileysmile:"))
        assertEquals("<glyph:clown>", ColorCodeUtils.emojiToGlyphTag(":clown:"))
        assertEquals("<glyph:fire>", ColorCodeUtils.emojiToGlyphTag(":fire:"))
    }

    @Test
    fun `passes non-discord values through unchanged`() {
        assertEquals(":weird", ColorCodeUtils.emojiToGlyphTag(":weird"))
        assertEquals("plain", ColorCodeUtils.emojiToGlyphTag("plain"))
        // ":": length 1, doesn't match the :name: guard -> passthrough (same as the legacy converter)
        assertEquals(":", ColorCodeUtils.emojiToGlyphTag(":"))
    }

    @Test
    fun `returns empty for null or blank`() {
        assertEquals("", ColorCodeUtils.emojiToGlyphTag(null))
        assertEquals("", ColorCodeUtils.emojiToGlyphTag(""))
    }
}
