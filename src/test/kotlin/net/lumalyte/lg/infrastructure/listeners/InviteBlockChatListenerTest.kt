package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.event.message.PreParseMessageEvent
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.message.MessageDirection
import dev.rosewood.rosechat.message.RoseMessage
import dev.rosewood.rosechat.message.RosePlayer
import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannel
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for personal invite blocking and compatibility boundaries. */
internal class InviteBlockChatListenerTest {
    /** Releases the test database or mocked server. */
    @AfterEach
    fun cleanup() = MockBukkit.unmock()

    /** Verifies blocks affect only that viewer and leave public chat and DMs visible. */
    @Test
    fun viewerFiltering() {
        MockBukkit.mock()
        val viewerId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val viewerPlayer = mockk<Player> { every { uniqueId } returns viewerId }
        val senderPlayer = mockk<Player> { every { uniqueId } returns senderId }
        val viewer = mockk<RosePlayer> { every { asPlayer() } returns viewerPlayer }
        val sender = mockk<RosePlayer> { every { asPlayer() } returns senderPlayer }
        val guild = mockk<LumaGuildsChannel>()
        val message =
            mockk<RoseMessage> {
                every { channel } returns guild
            }
        every { message.sender } returns sender
        val blocks = mockk<InviteBlockService>()
        every { blocks.hidesChat(viewerId, senderId) } returns true
        val listener = InviteBlockChatListener(blocks)

        fun event() = PreParseMessageEvent(message, viewer, MessageDirection.PLAYER_TO_SERVER)
        val hidden = event()
        listener.onPreParseMessage(hidden)
        assertTrue(hidden.isCancelled)
        every { blocks.hidesChat(viewerId, senderId) } returns false
        val visible = event()
        listener.onPreParseMessage(visible)
        assertFalse(visible.isCancelled)
        every { blocks.hidesChat(viewerId, senderId) } returns true
        every { message.channel } returns mockk<Channel>()
        val public = event()
        listener.onPreParseMessage(public)
        assertFalse(public.isCancelled)
        every { message.channel } returns null
        val dm = event()
        listener.onPreParseMessage(dm)
        assertFalse(dm.isCancelled)
    }
}
