package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuildEmojiSafetyTest {
    @Test
    fun `unresolved glyph cannot be selected or delegated to another renderer`() {
        val service = NexoEmojiService(mockk<ConfigService>(), NexoGlyphResolver { null })
        assertFalse(service.doesEmojiExist(":guild_bg_enthusia_6_row:"))
        assertEquals("", service.emojiToFontTag(":guild_bg_enthusia_6_row:"))
        assertEquals("Vegas", service.formatGuildDisplayName("Vegas", ":guild_bg_enthusia_6_row:"))
    }

    @Test
    fun `resolved menu glyph is rejected from every guild display format`() {
        val service = NexoEmojiService(mockk<ConfigService>(), NexoGlyphResolver {
            ResolvedNexoGlyph("ꐘ", "nexo:default", false, "guild_bg_enthusia_6_row")
        })
        val emoji = ":guild_bg_enthusia_6_row:"
        assertFalse(service.doesEmojiExist(emoji))
        assertEquals("", service.emojiToNexoPlaceholder(emoji))
        assertEquals("", service.emojiToGlyphTag(emoji))
        assertEquals("", service.emojiToFontTag(emoji))
        assertEquals("", service.getEmojiPlaceholder(emoji))
        assertEquals("Vegas", service.formatGuildDisplayName("Vegas", emoji))
    }

    @Test
    fun `registered emoji aliases preserve canonical id character and font`() {
        val service = NexoEmojiService(mockk<ConfigService>(), NexoGlyphResolver {
            ResolvedNexoGlyph("뀄", "nexo:default", true, "enthusia_logo")
        })
        assertTrue(service.doesEmojiExist(":enthusia:"))
        assertEquals("%nexo_enthusia_logo%", service.emojiToNexoPlaceholder(":enthusia:"))
        assertEquals("<glyph:enthusia_logo>", service.emojiToGlyphTag(":enthusia:"))
        assertEquals("<font:nexo:default>뀄</font>", service.emojiToFontTag(":enthusia:"))
        assertEquals(":enthusia_logo:", service.getEmojiPlaceholder(":enthusia:"))
    }

    @Test
    fun `malformed persisted values never reach the glyph resolver`() {
        val service = NexoEmojiService(mockk<ConfigService>(), NexoGlyphResolver {
            error("Malformed values must not be resolved")
        })
        listOf(":x><reset>:", "<glyph:guild_bg_enthusia_6_row>", "ꐘ").forEach {
            assertEquals("", service.emojiToNexoPlaceholder(it))
            assertEquals("", service.emojiToGlyphTag(it))
            assertEquals("", service.emojiToFontTag(it))
        }
    }

    @Test
    fun `resolver errors omit the emoji`() {
        val service = NexoEmojiService(mockk<ConfigService>(), NexoGlyphResolver {
            throw IllegalStateException("Reloading")
        })
        assertFalse(service.doesEmojiExist(":clown:"))
        assertEquals("", service.emojiToFontTag(":clown:"))
    }
}
