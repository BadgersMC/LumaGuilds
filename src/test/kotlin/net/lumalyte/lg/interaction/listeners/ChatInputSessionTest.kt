package net.lumalyte.lg.interaction.listeners

import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatInputSessionTest {
    @Test fun `old prompt timeout cannot clear replacement prompt`() {
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        val listener = ChatInputListener()
        val old = mockk<ChatInputHandler>()
        val latest = mockk<ChatInputHandler>()
        listener.startInputMode(player, old)
        listener.startInputMode(player, latest)
        assertFalse(listener.stopInputMode(player, old))
        assertTrue(listener.isInInputMode(player))
        assertTrue(listener.stopInputMode(player, latest))
        assertFalse(listener.isInInputMode(player))
    }

    @Test fun `disconnect clears private input before another session can submit`() {
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        val event = mockk<PlayerQuitEvent>()
        every { event.player } returns player
        val listener = ChatInputListener()
        listener.startInputMode(player, mockk())
        listener.onQuit(event)
        assertFalse(listener.isInInputMode(player))
    }
}
