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

@Suppress("FunctionNaming")
internal class QuickChatGuardPermissionTest {

    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()
    private val guildA = Guild(id = UUID.randomUUID(), name = "A", createdAt = Instant.now())
    private val guildB = Guild(id = UUID.randomUUID(), name = "B", createdAt = Instant.now())

    private companion object {
        const val NO_PERM = "no perm"
    }

    init {
        every { player.uniqueId } returns playerId
    }

    @Test
    fun `mod perm — single guild with MODERATE_CHAT succeeds`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns true
        assertTrue(
            player.requireGuildPermission(
                guildService, memberService,
                RankPermission.MODERATE_CHAT, NO_PERM,
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
                guildService, memberService,
                RankPermission.MODERATE_CHAT, NO_PERM,
            ),
        )
    }

    @Test
    fun `mod perm — multi-guild mod in guild B only succeeds`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns false
        every {
            memberService.hasPermission(playerId, guildB.id, RankPermission.MODERATE_CHAT)
        } returns true
        assertTrue(
            player.requireGuildPermission(
                guildService, memberService,
                RankPermission.MODERATE_CHAT, NO_PERM,
            ),
        )
    }

    @Test
    fun `mod perm — multi-guild no mod anywhere fails`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(playerId, guildA.id, RankPermission.MODERATE_CHAT)
        } returns false
        every {
            memberService.hasPermission(playerId, guildB.id, RankPermission.MODERATE_CHAT)
        } returns false
        assertFalse(
            player.requireGuildPermission(
                guildService, memberService,
                RankPermission.MODERATE_CHAT, NO_PERM,
            ),
        )
    }

    @Test
    fun `mod perm — no guilds fails`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertFalse(
            player.requireGuildPermission(
                guildService, memberService,
                RankPermission.MODERATE_CHAT, NO_PERM,
            ),
        )
    }

    @Test
    fun `announce — single guild with SEND_ANNOUNCEMENTS selects it`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(
                playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns true
        val result = player.requireGuildForPermission(
            guildService, memberService, RankPermission.SEND_ANNOUNCEMENTS, NO_PERM,
        )
        assertEquals(guildA.id, result?.id)
    }

    @Test
    fun `announce — single guild without SEND_ANNOUNCEMENTS returns null`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(
                playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns false
        assertNull(
            player.requireGuildForPermission(
                guildService, memberService,
                RankPermission.SEND_ANNOUNCEMENTS, NO_PERM,
            ),
        )
    }

    @Test
    fun `announce — multi-guild perm in guild B only selects guild B`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(
                playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns false
        every {
            memberService.hasPermission(
                playerId, guildB.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns true
        val result = player.requireGuildForPermission(
            guildService, memberService, RankPermission.SEND_ANNOUNCEMENTS, NO_PERM,
        )
        assertEquals(guildB.id, result?.id)
    }

    @Test
    fun `announce — no guilds returns null`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertNull(
            player.requireGuildForPermission(
                guildService, memberService,
                RankPermission.SEND_ANNOUNCEMENTS, NO_PERM,
            ),
        )
    }
}
