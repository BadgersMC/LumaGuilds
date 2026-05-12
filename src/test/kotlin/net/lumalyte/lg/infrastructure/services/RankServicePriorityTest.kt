package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PriorityDirection
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class RankServicePriorityTest {

    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()

    private fun mkRank(name: String, priority: Int, perms: Set<RankPermission> = emptySet()) =
        Rank(UUID.randomUUID(), guildId, name, priority, perms)

    private fun setup(
        actor: Rank,
        ranks: List<Rank>
    ): Triple<RankServiceBukkit, RankRepository, MemberRepository> {
        val rankRepo = mockk<RankRepository>(relaxed = true)
        val memberRepo = mockk<MemberRepository>(relaxed = true)
        ranks.forEach { every { rankRepo.getById(it.id) } returns it }
        every { rankRepo.getByGuild(guildId) } returns ranks.toSet()
        every { rankRepo.getHighestRank(guildId) } returns ranks.minByOrNull { it.priority }
        every { memberRepo.getRankId(actorId, guildId) } returns actor.id
        every { rankRepo.swapPriorities(any(), any()) } returns true
        val service = makeService(rankRepo, memberRepo)
        return Triple(service, rankRepo, memberRepo)
    }

    private fun makeService(rankRepo: RankRepository, memberRepo: MemberRepository): RankServiceBukkit {
        return RankServiceBukkit(
            rankRepository = rankRepo,
            memberRepository = memberRepo,
            guildRepository = mockk<GuildRepository>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true)
        )
    }

    @Test
    fun `moveRankPriority UP swaps with adjacent higher rank`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val mid = mkRank("Trusted", 4)
        val target = mkRank("Member", 5)
        val (svc, rankRepo) = setup(actor = owner, ranks = listOf(owner, mid, target))
        assertTrue(svc.moveRankPriority(target.id, PriorityDirection.UP, actorId))
        verify { rankRepo.swapPriorities(target.id, mid.id) }
    }

    @Test
    fun `moveRankPriority DOWN swaps with adjacent lower rank`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val target = mkRank("Trusted", 4)
        val below = mkRank("Member", 5)
        val (svc, rankRepo) = setup(actor = owner, ranks = listOf(owner, target, below))
        assertTrue(svc.moveRankPriority(target.id, PriorityDirection.DOWN, actorId))
        verify { rankRepo.swapPriorities(target.id, below.id) }
    }

    @Test
    fun `moveRankPriority fails when actor priority equal or higher than target`() {
        val actor2 = mkRank("Junior", 7, setOf(RankPermission.MANAGE_RANKS))
        val target2 = mkRank("Member", 5)
        val (svc, _) = setup(actor = actor2, ranks = listOf(actor2, target2))
        assertFalse(svc.moveRankPriority(target2.id, PriorityDirection.UP, actorId))
    }

    @Test
    fun `moveRankPriority UP fails when target is highest rank`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val target = owner
        val (svc, _) = setup(actor = owner, ranks = listOf(owner))
        assertFalse(svc.moveRankPriority(target.id, PriorityDirection.UP, actorId))
    }

    @Test
    fun `moveRankPriority DOWN fails when target is lowest rank`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val target = mkRank("Member", 5)
        val (svc, _) = setup(actor = owner, ranks = listOf(owner, target))
        assertFalse(svc.moveRankPriority(target.id, PriorityDirection.DOWN, actorId))
    }

    @Test
    fun `moveRankPriority fails when actor lacks MANAGE_RANKS`() {
        val actor = mkRank("Owner", 0) // no MANAGE_RANKS
        val target = mkRank("Member", 5)
        val (svc, _) = setup(actor = actor, ranks = listOf(actor, target))
        assertFalse(svc.moveRankPriority(target.id, PriorityDirection.UP, actorId))
    }
}
