package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REQ-221: what /pc does with its arguments; the party never has to be named. */
internal class PartyChatIntentTest {
    @Test
    fun noArgumentsToggles() = assertEquals(PartyChatIntent.Toggle, PartyChatIntent.parse(emptyList()))

    @Test
    fun wordsTurnPartyChatOnOrOff() {
        assertEquals(PartyChatIntent.On, PartyChatIntent.parse(listOf("on")))
        assertEquals(PartyChatIntent.On, PartyChatIntent.parse(listOf("switch")))
        assertEquals(PartyChatIntent.On, PartyChatIntent.parse(listOf("switch", "Unnamed")), "the party name is optional and ignored")
        assertEquals(PartyChatIntent.Off, PartyChatIntent.parse(listOf("switch", "global")))
        assertEquals(PartyChatIntent.Off, PartyChatIntent.parse(listOf("OFF")))
        assertEquals(PartyChatIntent.Off, PartyChatIntent.parse(listOf("clear")))
        assertEquals(PartyChatIntent.Mute, PartyChatIntent.parse(listOf("mute")))
        assertEquals(PartyChatIntent.Mute, PartyChatIntent.parse(listOf("toggle")))
        assertEquals(PartyChatIntent.Help, PartyChatIntent.parse(listOf("help")))
    }

    @Test
    fun anythingElseIsAMessage() {
        assertEquals(PartyChatIntent.Message("hello there"), PartyChatIntent.parse(listOf("hello", "there")))
        assertEquals(PartyChatIntent.Message("on my way"), PartyChatIntent.parse(listOf("on", "my", "way")))
    }
}
