package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankClaimPermissionProfileRepository
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

    private fun makeService(
        rankRepo: RankRepository,
        memberRepo: MemberRepository,
        rankClaimPermissionProfiles: RankClaimPermissionProfileRepository = mockk(relaxed = true),
        invalidatePlayer: (UUID) -> Unit = {},
        invalidateGuild: (UUID) -> Unit = {},
    ): RankServiceBukkit {
        return RankServiceBukkit(
            rankRepository = rankRepo,
            memberRepository = memberRepo,
            guildRepository = mockk<GuildRepository>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true),
            rankClaimPermissionProfiles = rankClaimPermissionProfiles,
            invalidateClaimPermissionCacheForPlayer = invalidatePlayer,
            invalidateClaimPermissionCacheForGuild = invalidateGuild,
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

    @Test
    fun `rank permission changes invalidate the guild claim permission cache`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val target = mkRank("Member", 5)
        val rankRepo = mockk<RankRepository>()
        val memberRepo = mockk<MemberRepository>()
        every { memberRepo.getRankId(actorId, guildId) } returns owner.id
        every { rankRepo.getById(owner.id) } returns owner
        every { rankRepo.getById(target.id) } returns target
        every { rankRepo.update(any()) } returns true

        val invalidated = mutableListOf<UUID>()
        val service = makeService(rankRepo, memberRepo, invalidateGuild = { invalidated += it })

        assertTrue(service.setRankPermissions(target.id, setOf(RankPermission.MANAGE_CLAIMS), actorId))
        assertEquals(listOf(guildId), invalidated)
    }

    @Test
    fun `assigning a rank invalidates that players claim permission cache`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_MEMBERS))
        val oldRank = mkRank("Member", 5)
        val newRank = mkRank("Trusted", 4)
        val playerId = UUID.randomUUID()
        val memberRepo = mockk<MemberRepository>()
        val rankRepo = mockk<RankRepository>()
        val existing = net.lumalyte.lg.domain.entities.Member(
            playerId, guildId, oldRank.id, java.time.Instant.now()
        )
        every { memberRepo.getRankId(actorId, guildId) } returns owner.id
        every { memberRepo.getByPlayerAndGuild(playerId, guildId) } returns existing
        every { rankRepo.getById(owner.id) } returns owner
        every { rankRepo.getById(newRank.id) } returns newRank
        every { memberRepo.update(any()) } returns true

        val invalidated = mutableListOf<UUID>()
        val service = makeService(rankRepo, memberRepo, invalidatePlayer = { invalidated += it })

        assertTrue(service.assignRank(playerId, guildId, newRank.id, actorId))
        assertEquals(listOf(playerId), invalidated)
    }

    @Test
    fun `rename preserves the old claim permission profile before updating the rank`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val target = mkRank("Member", 5)
        val rankRepo = mockk<RankRepository>()
        val memberRepo = mockk<MemberRepository>()
        val profiles = mockk<RankClaimPermissionProfileRepository>()
        every { memberRepo.getRankId(actorId, guildId) } returns owner.id
        every { rankRepo.getById(owner.id) } returns owner
        every { rankRepo.getById(target.id) } returns target
        every { rankRepo.isNameTaken(guildId, "Veteran") } returns false
        every { profiles.getOrCreate(target.id, "Member") } returns "Member"
        every { rankRepo.update(any()) } returns true

        val service = makeService(rankRepo, memberRepo, rankClaimPermissionProfiles = profiles)

        assertTrue(service.renameRank(target.id, "Veteran", actorId))
        verifyOrder {
            profiles.getOrCreate(target.id, "Member")
            rankRepo.update(match { it.id == target.id && it.name == "Veteran" })
        }
    }

    @Test
    fun `rename aborts when the stable claim permission profile cannot be persisted`() {
        val owner = mkRank("Owner", 0, setOf(RankPermission.MANAGE_RANKS))
        val target = mkRank("Member", 5)
        val rankRepo = mockk<RankRepository>()
        val memberRepo = mockk<MemberRepository>()
        val profiles = mockk<RankClaimPermissionProfileRepository>()
        every { memberRepo.getRankId(actorId, guildId) } returns owner.id
        every { rankRepo.getById(owner.id) } returns owner
        every { rankRepo.getById(target.id) } returns target
        every { rankRepo.isNameTaken(guildId, "Veteran") } returns false
        every { profiles.getOrCreate(target.id, "Member") } throws IllegalStateException("db unavailable")

        val service = makeService(rankRepo, memberRepo, rankClaimPermissionProfiles = profiles)

        assertFalse(service.renameRank(target.id, "Veteran", actorId))
        verify(exactly = 0) { rankRepo.update(any()) }
    }
}
