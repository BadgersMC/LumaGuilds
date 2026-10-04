package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val MENU_EMOJI = ":guild_bg_enthusia_6_row:"
private const val GUILD_NAME = "Vegas"
private const val IMP_ALIAS = ":enthusia:"
private const val GLYPH_FONT = "nexo:default"

/** Verifies validation, rendering, aliases and optional-plugin failure behavior. */
internal class GuildEmojiSafetyTest {
    private fun service(glyph: ResolvedNexoGlyph?) = NexoEmojiService(mockk(), NexoGlyphResolver { glyph })

    /** Unknown glyphs cannot be selected or delegated to a downstream renderer. */
    @Test
    fun rejectsUnknownGlyphs() {
        val service = service(null)
        assertFalse(service.doesEmojiExist(MENU_EMOJI))
        assertEquals("", service.emojiToFontTag(MENU_EMOJI))
        assertEquals(GUILD_NAME, service.formatGuildDisplayName(GUILD_NAME, MENU_EMOJI))
    }

    /** A resolved menu glyph is omitted in all guild display formats. */
    @Test
    fun omitsMenuGlyphs() {
        val service = service(ResolvedNexoGlyph("ꐘ", GLYPH_FONT, false, "guild_bg_enthusia_6_row"))
        assertFalse(service.doesEmojiExist(MENU_EMOJI))
        assertEquals("", service.emojiToNexoPlaceholder(MENU_EMOJI))
        assertEquals("", service.emojiToGlyphTag(MENU_EMOJI))
        assertEquals("", service.emojiToFontTag(MENU_EMOJI))
        assertEquals("", service.getEmojiPlaceholder(MENU_EMOJI))
        assertEquals(GUILD_NAME, service.formatGuildDisplayName(GUILD_NAME, MENU_EMOJI))
    }

    /** A valid alias renders the canonical emoji ID, font and character. */
    @Test
    fun preservesEmojiAliases() {
        val service = service(ResolvedNexoGlyph("뀄", GLYPH_FONT, true, "enthusia_logo"))
        assertTrue(service.doesEmojiExist(IMP_ALIAS))
        assertEquals("%nexo_enthusia_logo%", service.emojiToNexoPlaceholder(IMP_ALIAS))
        assertEquals("<glyph:enthusia_logo>", service.emojiToGlyphTag(IMP_ALIAS))
        assertEquals("<font:nexo:default>뀄</font>", service.emojiToFontTag(IMP_ALIAS))
        assertEquals(":enthusia_logo:", service.getEmojiPlaceholder(IMP_ALIAS))
    }

    /** Malformed saved values cannot reach glyph resolution. */
    @Test
    fun rejectsMalformedValues() {
        val resolver = NexoGlyphResolver { error("Malformed values must not be resolved") }
        val service = NexoEmojiService(mockk(), resolver)
        listOf(":x><reset>:", "<glyph:guild_bg_enthusia_6_row>", "ꐘ").forEach {
            assertEquals("", service.emojiToNexoPlaceholder(it))
            assertEquals("", service.emojiToGlyphTag(it))
            assertEquals("", service.emojiToFontTag(it))
        }
    }

    /** Temporary resolver unavailability hides the emoji instead of failing the display. */
    @Test
    fun handlesResolverFailure() {
        val resolver = NexoGlyphResolver { throw IllegalStateException("Reloading") }
        val service = NexoEmojiService(mockk(), resolver)
        assertFalse(service.doesEmojiExist(":clown:"))
        assertEquals("", service.emojiToFontTag(":clown:"))
    }
}
