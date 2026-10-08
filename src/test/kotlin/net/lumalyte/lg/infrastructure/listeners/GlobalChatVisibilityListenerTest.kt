package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.event.message.PreParseMessageEvent
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.message.MessageDirection
import dev.rosewood.rosechat.message.RoseMessage
import dev.rosewood.rosechat.message.RosePlayer
import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GlobalChatVisibilityListenerTest {
    @org.junit.jupiter.api.AfterEach fun cleanup() {
        org.mockbukkit.mockbukkit.MockBukkit
            .unmock()
    }

    @Test fun hidesOnlyGlobalPlayerChatter() {
        org.mockbukkit.mockbukkit.MockBukkit
            .mock()
        val player = mockk<Player>()
        val id = UUID.randomUUID()
        every { player.uniqueId } returns id
        val viewer = mockk<RosePlayer>()
        every { viewer.asPlayer() } returns player
        val sender = mockk<RosePlayer>()
        every { sender.isPlayer } returns true
        val global = mockk<Channel>()
        every { global.id } returns "public"
        val guild = mockk<Channel>()
        every { guild.id } returns "guild"
        val settings = mockk<ChatSettingsRepository>()
        every { settings.getVisibilitySettings(id) } returns ChatVisibilitySettings(id, globalChatVisible = false)
        val chat = mockk<RoseChatAdapter>()
        every { chat.getDefaultChannel() } returns global
        val message = mockk<RoseMessage>()
        every { message.sender } returns sender
        every { message.channel } returns global
        val listener = GlobalChatVisibilityListener(settings, chat)

        fun event() = PreParseMessageEvent(message, viewer, MessageDirection.PLAYER_TO_SERVER)
        val hidden = event()
        listener.onMessage(hidden)
        assertTrue(hidden.isCancelled)
        every { message.channel } returns guild
        val privateChannel = event()
        listener.onMessage(privateChannel)
        assertFalse(privateChannel.isCancelled)
        every { message.channel } returns null
        val dm = event()
        listener.onMessage(dm)
        assertFalse(dm.isCancelled)
        every { message.channel } returns global
        every { sender.isPlayer } returns false
        val notice = event()
        listener.onMessage(notice)
        assertFalse(notice.isCancelled)
    }
}
