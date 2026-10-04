package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests optional Nexo resolution and registered emoji font rendering. */
class NexoEmojiServiceFontTagTest {

    private val service = NexoEmojiService(mockk<ConfigService>())

    @Test
    fun `returns empty for null or blank`() {
        assertEquals("", service.emojiToFontTag(null))
        assertEquals("", service.emojiToFontTag(""))
        assertEquals("", service.emojiToFontTag("   "))
        assertEquals("", service.emojiToFontTag("\t\n"))
    }

    @Test
    fun `omits non-discord values`() {
        assertEquals("", service.emojiToFontTag(":weird"))
        assertEquals("", service.emojiToFontTag("plain"))
        assertEquals("", service.emojiToFontTag(":"))
    }

    @Test
    fun `rejects emoji names with MiniMessage control characters`() {
        assertEquals("", service.emojiToFontTag(":x><reset>:"))
        assertEquals("", service.emojiToFontTag(":<red>evil</red>:"))
        assertEquals("", service.emojiToFontTag(":emoji with spaces:"))
    }

    @Test
    fun `omits glyphs when Nexo is unavailable`() {
        assertEquals("", service.emojiToFontTag(":catsmileysmile:"))
        assertEquals("", service.emojiToFontTag(":clown:"))
        assertEquals("", service.emojiToFontTag(":fire:"))
    }

    @Test
    fun `formats guild display name with resolved glyph instead of persisted placeholder`() {
        val resolvedService = NexoEmojiService(
            mockk<ConfigService>(),
            NexoGlyphResolver { ResolvedNexoGlyph("\uE001", "nexo:emoji", true) }
        )

        assertEquals(
            "<font:nexo:emoji>\uE001</font> Enthusiast",
            resolvedService.formatGuildDisplayName("Enthusiast", ":enthusia_logo:")
        )
        assertEquals("Enthusiast", resolvedService.formatGuildDisplayName("Enthusiast", null))
    }

    @Test
    fun `renders a resolved public API glyph as a font tag`() {
        val resolvedService = NexoEmojiService(
            mockk<ConfigService>(),
            NexoGlyphResolver { ResolvedNexoGlyph("\uE001", "nexo:emoji", true) }
        )

        assertEquals(
            "<font:nexo:emoji>\uE001</font>",
            resolvedService.emojiToFontTag(":enthusia:")
        )
    }
}
