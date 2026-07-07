package net.lumalyte.lg.interaction.commands

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Suppress("FunctionNaming")
internal class QuickChatGuardAnnouncementTest {

    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()
    private val guildA = Guild(id = UUID.randomUUID(), name = "A", createdAt = Instant.now())
    private val guildB = Guild(id = UUID.randomUUID(), name = "B", createdAt = Instant.now())

    init {
        every { player.uniqueId } returns playerId
    }

    @Test
    fun `resolveAnnouncementGuild — exactly one authorized guild selected`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(
                playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns true
        assertEquals(
            guildA.id,
            resolveAnnouncementGuild(player, guildService, memberService),
        )
    }

    @Test
    fun `resolveAnnouncementGuild — no authorized guilds null`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA)
        every {
            memberService.hasPermission(
                playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns false
        assertNull(resolveAnnouncementGuild(player, guildService, memberService))
    }

    @Test
    fun `resolveAnnouncementGuild — multiple authorized guilds ambiguity rejected`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guildA, guildB)
        every {
            memberService.hasPermission(
                playerId, guildA.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns true
        every {
            memberService.hasPermission(
                playerId, guildB.id, RankPermission.SEND_ANNOUNCEMENTS,
            )
        } returns true
        assertNull(resolveAnnouncementGuild(player, guildService, memberService))
    }

    @Test
    fun `resolveAnnouncementGuild — no guilds null`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertNull(resolveAnnouncementGuild(player, guildService, memberService))
    }
}
