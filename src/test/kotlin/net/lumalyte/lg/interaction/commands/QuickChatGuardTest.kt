package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests for [QuickChatGuard] extension functions and multi-guild permission
 * selection behaviour.
 */
@Suppress("StringLiteralDuplication")
internal class QuickChatGuardTest {

    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()

    // Relaxed mock so sendMessage(String), sendMessage(Component), etc.
    // are all auto-stubbed without ambiguity errors.
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()

    private val guildA = Guild(id = UUID.randomUUID(), name = "A", createdAt = Instant.now())
    private val guildB = Guild(id = UUID.randomUUID(), name = "B", createdAt = Instant.now())

    init {
        every { player.uniqueId } returns playerId
    }

    // ══════════════════════════════════════════════════════════════════
    // requireGuildMembership
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `requireGuildMembership — has guilds returns true`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        assertTrue(player.requireGuildMembership(guildService))
    }

    @Test
    fun `requireGuildMembership — no guilds returns false`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertFalse(player.requireGuildMembership(guildService))
    }

    // ══════════════════════════════════════════════════════════════════
    // requireGuildPermission (MODERATE_CHAT across ALL guilds)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `mod perm — single guild with MODERATE_CHAT succeeds`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns true

        assertTrue(
            player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT, "no perm",
            ),
        )
    }

    @Test
    fun `mod perm — single guild without MODERATE_CHAT fails`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns false

        assertFalse(
            player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT, "no perm",
            ),
        )
    }

    @Test
    fun `mod perm — multi-guild, mod in guild B only, succeeds`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns false
        every {
            memberService.hasPermission(playerId, guildB.id, RankPermission.MODERATE_CHAT)
        } returns true

        assertTrue(
            player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT, "no perm",
            ),
        )
    }

    @Test
    fun `mod perm — multi-guild, no mod anywhere fails`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns false
        every {
            memberService.hasPermission(playerId, guildB.id, RankPermission.MODERATE_CHAT)
        } returns false

        assertFalse(
            player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT, "no perm",
            ),
        )
    }

    @Test
    fun `mod perm — no guilds fails`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()

        assertFalse(
            player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT, "no perm",
            ),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // requireGuildForPermission — SEND_ANNOUNCEMENTS guild selection
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `announce — single guild with SEND_ANNOUNCEMENTS selects it`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS)
        } returns true

        val result = player.requireGuildForPermission(
            guildService, memberService, RankPermission.SEND_ANNOUNCEMENTS, "no perm",
        )
        assertEquals(guildA.id, result?.id)
    }

    @Test
    fun `announce — single guild without SEND_ANNOUNCEMENTS returns null`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS)
        } returns false

        assertNull(
            player.requireGuildForPermission(
                guildService, memberService, RankPermission.SEND_ANNOUNCEMENTS, "no perm",
            ),
        )
    }

    @Test
    fun `announce — multi-guild, perm in guild B only, selects guild B`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS)
        } returns false
        every {
            memberService.hasPermission(playerId, guildB.id, RankPermission.SEND_ANNOUNCEMENTS)
        } returns true

        val result = player.requireGuildForPermission(
            guildService, memberService, RankPermission.SEND_ANNOUNCEMENTS, "no perm",
        )
        assertEquals(guildB.id, result?.id)
    }

    @Test
    fun `announce — no guilds returns null`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()

        assertNull(
            player.requireGuildForPermission(
                guildService, memberService, RankPermission.SEND_ANNOUNCEMENTS, "no perm",
            ),
        )
    }
}
