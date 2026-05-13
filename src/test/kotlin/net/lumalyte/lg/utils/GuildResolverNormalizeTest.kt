package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-Kotlin tests for [GuildResolver.normalize]. The other resolver methods
 * touch Bukkit and are exercised in-game.
 */
class GuildResolverNormalizeTest {

    @Test
    fun `lowercases plain input`() {
        assertEquals("knights", GuildResolver.normalize("Knights"))
        assertEquals("knights", GuildResolver.normalize("KNIGHTS"))
    }

    @Test
    fun `strips minimessage tags`() {
        assertEquals("knights", GuildResolver.normalize("<gradient:red:gold>Knights</gradient>"))
        assertEquals("knights", GuildResolver.normalize("<red>Knights</red>"))
    }

    @Test
    fun `strips legacy ampersand codes`() {
        assertEquals("knights", GuildResolver.normalize("&cKnights"))
        assertEquals("knights", GuildResolver.normalize("&6&lKnights"))
    }

    @Test
    fun `strips section codes`() {
        assertEquals("knights", GuildResolver.normalize("§cKnights"))
    }

    @Test
    fun `strips whitespace and punctuation`() {
        assertEquals("kingsguard", GuildResolver.normalize("King's Guard"))
        assertEquals("teamtwo", GuildResolver.normalize("Team-Two"))
    }

    @Test
    fun `blank input normalizes to empty string`() {
        assertEquals("", GuildResolver.normalize(""))
        assertEquals("", GuildResolver.normalize("   "))
        assertEquals("", GuildResolver.normalize("<red></red>"))
    }
}
