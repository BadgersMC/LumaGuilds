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
        val fixture = Fixture()
        val hidden = fixture.event()
        fixture.listener.onMessage(hidden)
        assertTrue(hidden.isCancelled)
        every { fixture.message.channel } returns fixture.guild
        val privateChannel = fixture.event()
        fixture.listener.onMessage(privateChannel)
        assertFalse(privateChannel.isCancelled)
        every { fixture.message.channel } returns null
        val dm = fixture.event()
        fixture.listener.onMessage(dm)
        assertFalse(dm.isCancelled)
        every { fixture.message.channel } returns fixture.global
        every { fixture.sender.isPlayer } returns false
        val notice = fixture.event()
        fixture.listener.onMessage(notice)
        assertFalse(notice.isCancelled)
    }

    private class Fixture {
        val id = UUID.randomUUID()
        val player = mockk<Player>()
        val viewer = mockk<RosePlayer>()
        val sender = mockk<RosePlayer>()
        val global = mockk<Channel>()
        val guild = mockk<Channel>()
        val settings = mockk<ChatSettingsRepository>()
        val chat = mockk<RoseChatAdapter>()
        val message = mockk<RoseMessage>()
        val listener = GlobalChatVisibilityListener(settings, chat)

        init {
            org.mockbukkit.mockbukkit.MockBukkit
                .mock()
            every { player.uniqueId } returns id
            every { viewer.asPlayer() } returns player
            every { sender.isPlayer } returns true
            every { global.id } returns "public"
            every { guild.id } returns "guild"
            every { settings.getVisibilitySettings(id) } returns ChatVisibilitySettings(id, globalChatVisible = false)
            every { chat.getDefaultChannel() } returns global
            every { message.sender } returns sender
            every { message.channel } returns global
        }

        fun event() = PreParseMessageEvent(message, viewer, MessageDirection.PLAYER_TO_SERVER)
    }
}
