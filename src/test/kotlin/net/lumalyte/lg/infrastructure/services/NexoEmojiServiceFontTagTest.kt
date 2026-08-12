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
    fun `falls back to glyph tag when Nexo is unavailable`() {
        // No Bukkit server in unit tests -> getFontManager() is null -> <glyph:name>
        assertEquals("<glyph:catsmileysmile>", service.emojiToFontTag(":catsmileysmile:"))
        assertEquals("<glyph:clown>", service.emojiToFontTag(":clown:"))
        assertEquals("<glyph:fire>", service.emojiToFontTag(":fire:"))
    }
}
