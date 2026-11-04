package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse

internal class MemberBypassProtectionTest {
    private val guild = UUID.randomUUID()
    private val staffActor = UUID.randomUUID()
    private val staffTarget = UUID.randomUUID()
    private val members = mockk<MemberRepository>(relaxed = true)
    private val ranks = mockk<RankRepository>(relaxed = true)
    private val bypass = mockk<AdminOverrideService>(relaxed = true)
    private val service = MemberServiceBukkit(
        members, ranks, mockk<GuildRepository>(relaxed = true),
        mockk<ProgressionRepository>(relaxed = true),
        mockk<ProgressionConfigService>(relaxed = true),
        mockk<MembershipHistoryRepository>(relaxed = true), bypass,
    )

    @Test
    fun `staff with active bypass cannot be kicked even by another bypassed admin`() {
        every { bypass.hasOverride(staffActor) } returns true
        every { bypass.hasOverride(staffTarget) } returns true
        assertFalse(service.removeMember(staffTarget, guild, staffActor))
        verify(exactly = 0) { members.remove(any(), any()) }
    }

    @Test
    fun `staff with active bypass cannot be demoted or reassigned by another admin`() {
        every { bypass.hasOverride(staffActor) } returns true
        every { bypass.hasOverride(staffTarget) } returns true
        val memberRank = Rank(UUID.randomUUID(), guild, "Member", 4)
        every { members.getByPlayerAndGuild(staffTarget, guild) } returns
            Member(staffTarget, guild, memberRank.id, Instant.EPOCH)
        every { ranks.getById(memberRank.id) } returns memberRank

        assertFalse(service.changeMemberRank(staffTarget, guild, memberRank.id, staffActor))
        assertFalse(service.promoteMember(staffTarget, guild, staffActor))
        assertFalse(service.demoteMember(staffTarget, guild, staffActor))
        verify(exactly = 0) { members.update(any()) }
    }
}
