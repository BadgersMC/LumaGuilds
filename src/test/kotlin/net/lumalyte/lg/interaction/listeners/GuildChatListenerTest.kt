package net.lumalyte.lg.interaction.listeners

import dev.rosewood.rosechat.chat.channel.Channel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ChatChannelIds
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

@Suppress("StringLiteralDuplication")
internal class GuildChatListenerTest {

    private val adapter = mockk<RoseChatAdapter>()
    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()

    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()

    private val modChannel = mockk<Channel>()
    private val defaultChannel = mockk<Channel>()
    private val otherChannel = mockk<Channel>()

    private val guildA = Guild(id = UUID.randomUUID(), name = "A", createdAt = Instant.now())

    private lateinit var listener: GuildChatListener

    @BeforeEach
    fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                single { guildService }
                single { memberService }
                single { GuildChatListener() }
            })
        }

        every { player.uniqueId } returns playerId
        every { modChannel.id } returns ChatChannelIds.MODCHAT
        every { defaultChannel.id } returns "global"
        every { otherChannel.id } returns "local"

        listener = GuildChatListener().apply {
            adapter = this@GuildChatListenerTest.adapter
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Channel missing
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleModChat — channel missing returns null`() {
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns null

        val result = listener.toggleModChat(player)
        assertNull(result)
        verify { player.sendMessage(any<String>()) }
    }

    // ══════════════════════════════════════════════════════════════════
    // Already in mod chat → leave
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleModChat — already in mod chat, leaves successfully`() {
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns modChannel
        every { adapter.getCurrentChannel(player) } returns modChannel
        every { adapter.getDefaultChannel() } returns defaultChannel
        every { adapter.switchChannel(player, defaultChannel) } just runs

        val result = listener.toggleModChat(player)
        assertFalse(result!!)
        verify(exactly = 1) { adapter.switchChannel(player, defaultChannel) }
    }

    // ══════════════════════════════════════════════════════════════════
    // Enter mod chat with permission
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleModChat — enters mod chat with permission`() {
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns modChannel
        every { adapter.getCurrentChannel(player) } returns otherChannel
        every { adapter.switchChannel(player, modChannel) } just runs
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns true

        val result = listener.toggleModChat(player)
        assertTrue(result!!)
        verify(exactly = 1) { adapter.switchChannel(player, modChannel) }
    }

    // ══════════════════════════════════════════════════════════════════
    // Enter without permission → rejected
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleModChat — entering without permission rejected`() {
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns modChannel
        every { adapter.getCurrentChannel(player) } returns otherChannel
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns false

        val result = listener.toggleModChat(player)
        assertNull(result)
        verify { player.sendMessage("§c❌ Only guild moderators can use mod chat!") }
        verify(exactly = 0) { adapter.switchChannel(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════
    // Enter without guild → rejected
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleModChat — entering without guild rejected`() {
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns modChannel
        every { adapter.getCurrentChannel(player) } returns otherChannel
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()

        val result = listener.toggleModChat(player)
        assertNull(result)
        verify { player.sendMessage("§c❌ You are not in a guild!") }
        verify(exactly = 0) { adapter.switchChannel(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════
    // Demoted moderator exit path
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleModChat — demoted moderator can always leave mod chat`() {
        // Simulate: player is already in mod chat, but MODERATE_CHAT was revoked.
        // The leave path must skip permission checks entirely.
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns modChannel
        every { adapter.getCurrentChannel(player) } returns modChannel
        every { adapter.getDefaultChannel() } returns defaultChannel
        every { adapter.switchChannel(player, defaultChannel) } just runs

        val result = listener.toggleModChat(player)

        assertFalse(result!!)
        verify(exactly = 1) { adapter.switchChannel(player, defaultChannel) }
        // Guild and permission checks must NOT be consulted on the leave path.
        verify(exactly = 0) { guildService.getPlayerGuilds(any()) }
        verify(exactly = 0) { memberService.hasPermission(any(), any(), any()) }
        // No denial message.
        verify(exactly = 0) { player.sendMessage(ofType(String::class)) }
    }

    @Test
    fun `toggleModChat — demoted moderator restoring previous channel`() {
        val prevChannel = mockk<Channel>()
        every { prevChannel.id } returns "staff"

        // First: enter mod chat (caches previous channel = otherChannel).
        every { adapter.getChannel(ChatChannelIds.MODCHAT) } returns modChannel
        every { adapter.getCurrentChannel(player) } returnsMany listOf(otherChannel, modChannel, modChannel)
        every { adapter.switchChannel(player, modChannel) } just runs
        every { adapter.switchChannel(player, prevChannel) } just runs
        every { adapter.getChannel("local") } returns prevChannel
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns true

        // Enter mod chat — caches "local" as previous.
        val enterResult = listener.toggleModChat(player)
        assertTrue(enterResult!!)
        verify(exactly = 1) { adapter.switchChannel(player, modChannel) }

        // Second: leave mod chat — should restore "local" (cached previous).
        // Now simulate no permission (demoted) — leave path must still work.
        every { adapter.getCurrentChannel(player) } returns modChannel

        val leaveResult = listener.toggleModChat(player)
        assertFalse(leaveResult!!)
        verify(exactly = 1) { adapter.switchChannel(player, prevChannel) }
        // Permission check never called on leave.
    }

    // ══════════════════════════════════════════════════════════════════
    // Toggle guild chat — basic paths
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleGuildChat — channel missing returns false`() {
        every { adapter.getChannel(ChatChannelIds.GUILD) } returns null

        val result = listener.toggleGuildChat(player)
        assertFalse(result)
        verify { player.sendMessage(any<String>()) }
    }

    @Test
    fun `toggleGuildChat — entering without guild rejected`() {
        val guildChannel = mockk<Channel>()
        every { guildChannel.id } returns ChatChannelIds.GUILD
        every { adapter.getChannel(ChatChannelIds.GUILD) } returns guildChannel
        every { adapter.getCurrentChannel(player) } returns otherChannel
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()

        val result = listener.toggleGuildChat(player)
        assertFalse(result)
        verify { player.sendMessage("§c❌ You are not in a guild!") }
    }
}
