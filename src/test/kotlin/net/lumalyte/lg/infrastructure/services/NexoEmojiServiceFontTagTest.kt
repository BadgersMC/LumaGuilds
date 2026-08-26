package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [NexoEmojiService.emojiToFontTag] — the guild-emoji → raw
 * `<font:<glyphfont>><char></font>` converter used by
 * %lumaguilds_guild_emoji_font% (backend MiniMessage consumers: UnlimitedNameTags,
 * IC-DiscordSRV-Addon playerlist image renderer, Velocitab via PapiProxyBridge).
 *
 * Without a running Bukkit server [NexoEmojiService.getFontManager] resolves to null,
 * so these tests exercise the fallback path (Nexo `<glyph:name>` tag, matching
 * [net.lumalyte.lg.utils.ColorCodeUtils.emojiToGlyphTag]). The Nexo char/font
 * extraction itself is integration-only (requires live Nexo classes on a server).
 */
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
    fun `passes non-discord values through unchanged`() {
        assertEquals(":weird", service.emojiToFontTag(":weird"))
        assertEquals("plain", service.emojiToFontTag("plain"))
        // ":": length 1, doesn't match the :name: guard -> passthrough (same as the glyph-tag converter)
        assertEquals(":", service.emojiToFontTag(":"))
    }

    @Test
    fun `rejects emoji names with MiniMessage control characters`() {
        // isValidEmojiFormat only checks delimiters; the glyph-id guard must stop
        // MiniMessage control characters from reaching the generated tag.
        assertEquals(":x><reset>:", service.emojiToFontTag(":x><reset>:"))
        assertEquals(":<red>evil</red>:", service.emojiToFontTag(":<red>evil</red>:"))
        assertEquals(":emoji with spaces:", service.emojiToFontTag(":emoji with spaces:"))
    }

    @Test
    fun `falls back to glyph tag when Nexo is unavailable`() {
        // No Bukkit server in unit tests -> getFontManager() is null -> <glyph:name>
        assertEquals("<glyph:catsmileysmile>", service.emojiToFontTag(":catsmileysmile:"))
        assertEquals("<glyph:clown>", service.emojiToFontTag(":clown:"))
        assertEquals("<glyph:fire>", service.emojiToFontTag(":fire:"))
    }

    @Test
    fun `renders a resolved public API glyph as a font tag`() {
        val resolvedService = NexoEmojiService(
            mockk<ConfigService>(),
            NexoGlyphResolver { ResolvedNexoGlyph("\uE001", "nexo:emoji") }
        )

        assertEquals(
            "<font:nexo:emoji>\uE001</font>",
            resolvedService.emojiToFontTag(":enthusia:")
        )
    }
}
