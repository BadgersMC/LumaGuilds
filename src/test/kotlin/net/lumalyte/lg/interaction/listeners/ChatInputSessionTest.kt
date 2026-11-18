package net.lumalyte.lg.interaction.listeners

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ChatInputSessionTest {
    private fun listener() = ChatInputListener(mockk<ConfigService>())

    @org.junit.jupiter.api.DisplayName("listener is constructor injected without Koin service location")
    @Test
    fun constructorInjectedWithoutKoin() {
        assertFalse(KoinComponent::class.java.isAssignableFrom(ChatInputListener::class.java))
    }

    @org.junit.jupiter.api.DisplayName("old prompt timeout cannot clear replacement prompt")
    @Test
    fun oldPromptTimeoutCannotCle1() {
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        val listener = listener()
        val old = mockk<ChatInputHandler>()
        val latest = mockk<ChatInputHandler>()
        listener.startInputMode(player, old)
        listener.startInputMode(player, latest)
        assertFalse(listener.stopInputMode(player, old))
        assertTrue(listener.isInInputMode(player))
        assertTrue(listener.stopInputMode(player, latest))
        assertFalse(listener.isInInputMode(player))
    }

    @org.junit.jupiter.api.DisplayName("disconnect clears private input before another session can submit")
    @Test
    fun disconnectClearsPrivateIn2() {
        val player = mockk<Player>()
        every { player.uniqueId } returns UUID.randomUUID()
        val event = mockk<PlayerQuitEvent>()
        every { event.player } returns player
        val listener = listener()
        listener.startInputMode(player, mockk())
        listener.onQuit(event)
        assertFalse(listener.isInInputMode(player))
    }
}
