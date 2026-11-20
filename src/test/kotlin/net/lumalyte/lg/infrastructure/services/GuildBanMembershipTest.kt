package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildBanRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** REQ-194 (no banned player is added) and REQ-197 (kicks need a higher rank). */
class GuildBanMembershipTest {
    private val guildId = UUID.randomUUID()
    private val members = mockk<MemberRepository>(relaxed = true)
    private val ranks = mockk<RankRepository>()
    private val guilds = mockk<GuildRepository>()
    private val bans = mockk<GuildBanRepository>()
    private val recruit = Rank(UUID.randomUUID(), guildId, "Recruit", 9, emptySet())
    private val officer = Rank(UUID.randomUUID(), guildId, "Officer", 2, setOf(RankPermission.MANAGE_MEMBERS))
    private val coLeader = Rank(UUID.randomUUID(), guildId, "Co-Leader", 1, setOf(RankPermission.MANAGE_MEMBERS))

    init {
        every { guilds.getById(guildId) } returns Guild(guildId, "Vibe", createdAt = Instant.now())
        listOf(recruit, officer, coLeader).forEach { every { ranks.getById(it.id) } returns it }
        every { ranks.getByGuild(guildId) } returns setOf(recruit, officer, coLeader)
        every { members.getByGuild(guildId) } returns emptySet()
        every { members.isPlayerInGuild(any(), guildId) } returns false
    }

    private fun service() = MemberServiceBukkit(
        members, ranks, guilds, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), null, guildBans = bans,
    )

    @Test
    fun `a banned player is never added`() {
        val player = UUID.randomUUID()
        every { bans.isBanned(guildId, player) } returns true
        assertNull(service().addMember(player, guildId, recruit.id))
        verify(exactly = 0) { members.add(any()) }
    }

    @Test
    fun `an unreadable ban list refuses the join`() {
        val player = UUID.randomUUID()
        every { bans.isBanned(guildId, player) } throws IllegalStateException("db down")
        assertNull(service().addMember(player, guildId, recruit.id))
        verify(exactly = 0) { members.add(any()) }
    }

    @Test
    fun `an officer cannot remove a co-leader`() {
        val actor = UUID.randomUUID()
        val target = UUID.randomUUID()
        every { members.isPlayerInGuild(target, guildId) } returns true
        every { members.getByPlayerAndGuild(actor, guildId) } returns Member(actor, guildId, officer.id, Instant.now())
        every { members.getByPlayerAndGuild(target, guildId) } returns Member(target, guildId, coLeader.id, Instant.now())
        assertFalse(service().removeMember(target, guildId, actor))
        verify(exactly = 0) { members.remove(target, guildId) }
    }
}
