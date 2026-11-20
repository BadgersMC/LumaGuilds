package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * REQ-006: `colored_chat_enabled` gates legacy § color codes in chat messages.
 * The strip helper removes § codes while keeping MiniMessage tags and emoji glyphs.
 */
class ChatServiceBukkitTest {

    @Test
    fun `strips legacy section color codes`() {
        assertEquals("Hello World", ChatServiceBukkit.stripLegacyColors("§cHello §lWorld"))
    }

    @Test
    fun `strips hex color codes`() {
        assertEquals("Hi", ChatServiceBukkit.stripLegacyColors("§x§f§f§0§0§0§0Hi"))
    }

    @Test
    fun `keeps plain text unchanged`() {
        assertEquals("plain message", ChatServiceBukkit.stripLegacyColors("plain message"))
    }

    @Test
    fun `keeps minimessage tags and emoji glyphs`() {
        assertEquals("<red>Hi <glyph:emoji1>", ChatServiceBukkit.stripLegacyColors("<red>Hi <glyph:emoji1>"))
    }

    @Test
    fun `strips multiple codes across message`() {
        assertEquals("AB", ChatServiceBukkit.stripLegacyColors("§aA§bB"))
    }

    @Test
    fun `strips uppercase legacy codes`() {
        assertEquals("Hi", ChatServiceBukkit.stripLegacyColors("§AHi"))
    }

    @Test
    fun `strips uppercase bungee rgb sequences`() {
        assertEquals("Hi", ChatServiceBukkit.stripLegacyColors("§X§R§F§F§0§0§0§0Hi"))
    }
}

/** Party and guild chat crashed when a guild's emoji glyph no longer resolved (TEST, 2026-11-10). */
class ChatServiceGuildTagEmojiTest {
    @org.junit.jupiter.api.Test
    fun `an emoji that no longer resolves is dropped instead of breaking the guild copy`() {
        kotlin.test.assertEquals(null, ChatServiceBukkit.displayEmoji(""))
        kotlin.test.assertEquals(null, ChatServiceBukkit.displayEmoji(null))
        kotlin.test.assertEquals(":crown:", ChatServiceBukkit.displayEmoji(":crown:"))
        kotlin.test.assertEquals(null, ChatServiceBukkit.displayEmoji(""), "a rendered glyph is not a Nexo placeholder")
    }
}
