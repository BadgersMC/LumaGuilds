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

internal class MemberOwnerMutationBoundaryTest {
    @Test
    fun adminOwnerDemotionRejected() {
        val guildId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val ownerRank = Rank(UUID.randomUUID(), guildId, "Owner", OWNER_PRIORITY)
        val memberRank = Rank(UUID.randomUUID(), guildId, "Member", MEMBER_PRIORITY)
        val owner = Member(ownerId, guildId, ownerRank.id, Instant.EPOCH)

        val members = mockk<MemberRepository>()
        val ranks = mockk<RankRepository>()
        val override = mockk<AdminOverrideService>()
        every { override.hasOverride(adminId) } returns true
        every { members.getByPlayerAndGuild(ownerId, guildId) } returns owner
        every { ranks.getById(ownerRank.id) } returns ownerRank
        every { ranks.getById(memberRank.id) } returns memberRank

        val service =
            MemberServiceBukkit(
                members,
                ranks,
                mockk<GuildRepository>(relaxed = true),
                mockk<ProgressionRepository>(relaxed = true),
                mockk<ProgressionConfigService>(relaxed = true),
                mockk<MembershipHistoryRepository>(relaxed = true),
                override,
            )

        assertFalse(service.changeMemberRank(ownerId, guildId, memberRank.id, adminId))
        verify(exactly = 0) { members.update(any()) }
    }

    private companion object {
        const val OWNER_PRIORITY = 0
        const val MEMBER_PRIORITY = 4
    }
}
