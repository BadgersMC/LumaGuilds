package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankClaimPermissionProfileRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Direct service calls must be as restrictive as the rank/member menus. */
@Suppress("TooManyFunctions") // Independent authority cases share one small service fixture.
internal class RankAuthorityBoundaryTest {
    private val guild = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val ranks = mockk<RankRepository>(relaxed = true)
    private val members = mockk<MemberRepository>(relaxed = true)
    private val memberService = mockk<MemberService>(relaxed = true)
    private val service =
        RankServiceBukkit(
            ranks,
            members,
            mockk<GuildRepository>(relaxed = true),
            memberService,
            mockk<RankClaimPermissionProfileRepository>(relaxed = true),
        )
    private val management = setOf(RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS)

    private fun rank(priority: Int, permissions: Set<RankPermission> = management): Rank =
        Rank(UUID.randomUUID(), guild, "Rank$priority", priority, permissions).also {
            every { ranks.getById(it.id) } returns it
        }

    private fun actorRank(priority: Int = ACTOR_PRIORITY): Rank {
        return rank(priority).also {
            every { members.getRankId(actor, guild) } returns it.id
        }
    }

    private fun member(player: UUID, rank: Rank) {
        every { members.getByPlayerAndGuild(player, guild) } returns Member(player, guild, rank.id, Instant.EPOCH)
    }

    @Test fun selfPromotionRejected() {
        val current = actorRank()
        member(actor, current)
        val higher = rank(1)
        assertFalse(service.assignRank(actor, guild, higher.id, actor))
        verify(exactly = 0) { members.update(any()) }
    }

    @Test fun ownerAssignmentRejected() {
        actorRank(0)
        val lower = rank(LOWER_PRIORITY)
        member(target, lower)
        assertFalse(service.assignRank(target, guild, rank(0).id, actor))
        verify(exactly = 0) { members.update(any()) }
    }

    @Test fun ownerDemotionRejected() {
        actorRank()
        member(target, rank(0))
        assertFalse(service.assignRank(target, guild, rank(LOWER_PRIORITY).id, actor))
        verify(exactly = 0) { members.update(any()) }
    }

    @Test fun peerMutationRejected() {
        actorRank()
        val peer = rank(ACTOR_PRIORITY)
        assertFalse(service.renameRank(peer.id, "Changed", actor))
        assertFalse(service.setRankPermissions(peer.id, emptySet(), actor))
        assertFalse(service.addRankPermission(peer.id, RankPermission.WITHDRAW_FROM_BANK, actor))
        assertFalse(service.removeRankPermission(peer.id, RankPermission.MANAGE_RANKS, actor))
        assertFalse(service.deleteRank(peer.id, actor))
        verify(exactly = 0) { ranks.update(any()) }
        verify(exactly = 0) { ranks.remove(any()) }
    }

    @Test fun privilegeDelegationRejected() {
        actorRank()
        val lower = rank(LOWER_PRIORITY, emptySet())
        assertFalse(service.addRankPermission(lower.id, RankPermission.WITHDRAW_FROM_BANK, actor))
        assertFalse(service.setRankPermissions(lower.id, setOf(RankPermission.WITHDRAW_FROM_BANK), actor))
        assertFalse(service.updateRank(lower.copy(permissions = setOf(RankPermission.WITHDRAW_FROM_BANK)), actor))
        every { ranks.getNextPriority(guild) } returns LOWEST_PRIORITY
        assertTrue(service.addRank(guild, "New", setOf(RankPermission.WITHDRAW_FROM_BANK), actor) == null)
        verify(exactly = 0) { ranks.update(any()) }
        verify(exactly = 0) { ranks.add(any()) }
    }

    @Test fun editCannotChangePriority() {
        actorRank()
        val lower = rank(LOWER_PRIORITY)
        assertFalse(service.updateRank(lower.copy(priority = 0), actor))
        verify(exactly = 0) { ranks.update(any()) }
    }

    @Test fun lowerAssignmentAllowed() {
        actorRank()
        member(target, rank(LOWEST_PRIORITY))
        val destination = rank(LOWER_PRIORITY)
        every { members.update(any()) } returns true
        assertTrue(service.assignRank(target, guild, destination.id, actor))
    }

    @Test fun hiddenPermissionPreserved() {
        actorRank()
        val lower = rank(LOWER_PRIORITY, setOf(RankPermission.WITHDRAW_FROM_BANK))
        every { ranks.update(any()) } returns true
        assertTrue(service.updateRank(lower.copy(icon = "BOOK"), actor))
        verify { ranks.update(match { RankPermission.WITHDRAW_FROM_BANK in it.permissions }) }
    }

    private companion object {
        const val ACTOR_PRIORITY = 2
        const val LOWER_PRIORITY = 4
        const val LOWEST_PRIORITY = 5
    }
}
