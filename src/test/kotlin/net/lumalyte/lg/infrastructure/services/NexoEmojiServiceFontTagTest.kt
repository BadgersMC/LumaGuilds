package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests optional Nexo resolution and registered emoji font rendering. */
internal class NexoEmojiServiceFontTagTest {

    private val service = NexoEmojiService(mockk<ConfigService>())

    /** Null and blank values render as empty text. */
    @Test
    fun omitsBlankValues() {
        assertEquals("", service.emojiToFontTag(null))
        assertEquals("", service.emojiToFontTag(""))
        assertEquals("", service.emojiToFontTag("   "))
        assertEquals("", service.emojiToFontTag("\t\n"))
    }

    /** Non-placeholder input is never delegated to a glyph renderer. */
    @Test
    fun omitsPlainValues() {
        assertEquals("", service.emojiToFontTag(":weird"))
        assertEquals("", service.emojiToFontTag("plain"))
        assertEquals("", service.emojiToFontTag(":"))
    }

    /** Control characters cannot enter generated MiniMessage tags. */
    @Test
    fun rejectsControlCharacters() {
        assertEquals("", service.emojiToFontTag(":x><reset>:"))
        assertEquals("", service.emojiToFontTag(":<red>evil</red>:"))
        assertEquals("", service.emojiToFontTag(":emoji with spaces:"))
    }

    /** Missing Nexo hides emojis instead of emitting unvalidated glyph tags. */
    @Test
    fun handlesAbsentNexo() {
        assertEquals("", service.emojiToFontTag(":catsmileysmile:"))
        assertEquals("", service.emojiToFontTag(":clown:"))
        assertEquals("", service.emojiToFontTag(":fire:"))
    }

    /** Resolved emoji fonts prefix guild names without changing their text. */
    @Test
    fun formatsGuildName() {
        val resolvedService = NexoEmojiService(
            mockk<ConfigService>(),
            NexoGlyphResolver { ResolvedNexoGlyph("\uE001", "nexo:emoji", true) },
        )

        assertEquals(
            "<font:nexo:emoji>\uE001</font> Enthusiast",
            resolvedService.formatGuildDisplayName("Enthusiast", ":enthusia_logo:")
        )
        assertEquals("Enthusiast", resolvedService.formatGuildDisplayName("Enthusiast", null))
    }

    /** Registered emoji characters retain their resource-pack font. */
    @Test
    fun rendersEmojiFont() {
        val resolvedService = NexoEmojiService(
            mockk<ConfigService>(),
            NexoGlyphResolver { ResolvedNexoGlyph("\uE001", "nexo:emoji", true) },
        )

        assertEquals(
            "<font:nexo:emoji>\uE001</font>",
            resolvedService.emojiToFontTag(":enthusia:")
        )
    }
}
