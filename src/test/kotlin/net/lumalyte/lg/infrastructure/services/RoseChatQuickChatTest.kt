package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.Channel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("StringLiteralDuplication", "FunctionNaming")
internal class RoseChatQuickChatTest {
    private val mockAdapter = mockk<RoseChatAdapter>()
    private val player = mockk<Player>(relaxed = true)
    private val channel = mockk<Channel>()

    @BeforeEach
    fun setUp() {
        RoseChatQuickChat.adapter = mockAdapter
    }

    @AfterEach
    fun tearDown() {
        RoseChatQuickChat.adapter = RealRoseChatAdapter()
    }

    // ══════════════════════════════════════════════════════════════════
    // EmptyMessage
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `blank message returns EmptyMessage`() {
        assertSame(
            RoseChatQuickChat.Result.EmptyMessage,
            RoseChatQuickChat.send(player, "guild", "   "),
        )
    }

    @Test
    fun `empty string returns EmptyMessage`() {
        assertSame(
            RoseChatQuickChat.Result.EmptyMessage,
            RoseChatQuickChat.send(player, "guild", ""),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // ChannelMissing
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `missing channel returns ChannelMissing`() {
        every { mockAdapter.getChannel("guild-ally") } returns null

        assertSame(
            RoseChatQuickChat.Result.ChannelMissing,
            RoseChatQuickChat.send(player, "guild-ally", "hello"),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Channel ID resolution
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `guild channel ID resolved correctly`() {
        every { mockAdapter.getChannel("guild") } returns channel
        every { mockAdapter.quickChat(player, channel, "test") } just runs

        val result = RoseChatQuickChat.send(player, "guild", "test")
        assertSame(RoseChatQuickChat.Result.Dispatched, result)
        verify { mockAdapter.getChannel("guild") }
    }

    @Test
    fun `guild-ally channel ID resolved correctly`() {
        every { mockAdapter.getChannel("guild-ally") } returns channel
        every { mockAdapter.quickChat(player, channel, "test") } just runs

        val result = RoseChatQuickChat.send(player, "guild-ally", "test")
        assertSame(RoseChatQuickChat.Result.Dispatched, result)
        verify { mockAdapter.getChannel("guild-ally") }
    }

    @Test
    fun `guild-modchat channel ID resolved correctly`() {
        every { mockAdapter.getChannel("guild-modchat") } returns channel
        every { mockAdapter.quickChat(player, channel, "test") } just runs

        val result = RoseChatQuickChat.send(player, "guild-modchat", "test")
        assertSame(RoseChatQuickChat.Result.Dispatched, result)
        verify { mockAdapter.getChannel("guild-modchat") }
    }

    // ══════════════════════════════════════════════════════════════════
    // quickChat invoked once
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `quickChat invoked exactly once on success`() {
        every { mockAdapter.getChannel("guild") } returns channel
        every { mockAdapter.quickChat(player, channel, "hello") } just runs

        RoseChatQuickChat.send(player, "guild", "hello")
        verify(exactly = 1) { mockAdapter.quickChat(player, channel, "hello") }
    }

    // ══════════════════════════════════════════════════════════════════
    // current channel unchanged
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `current channel unchanged after quickChat`() {
        every { mockAdapter.getChannel("guild") } returns channel
        every { mockAdapter.quickChat(player, channel, "msg") } just runs

        RoseChatQuickChat.send(player, "guild", "msg")
        verify(exactly = 1) { mockAdapter.quickChat(player, channel, "msg") }
    }
}
