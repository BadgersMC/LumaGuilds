package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Regression tests for the treaty/relation lifecycle fixes:
 * - stale terminal rows must not block new alliance requests (they are reused in place)
 * - rejecting/cancelling a truce or unenemy request must keep the guilds at war
 * - a pending de-escalation request must read as ENEMY until accepted
 * - cleanup purges terminal rows and auto-resolves long-pending requests
 */
class RelationServiceTreatyTest {

    private val guildA: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val guildB: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val actorId: UUID = UUID.randomUUID()

    private fun service(
        repo: RelationRepository
    ): RelationServiceBukkit {
        val memberService = mockk<MemberService>(relaxed = true)
        every { memberService.getPlayerGuilds(any()) } returns setOf(guildA, guildB)
        every { memberService.hasPermission(any(), any(), RankPermission.MANAGE_RELATIONS) } returns true
        val guildRepository = mockk<GuildRepository>(relaxed = true)
        return RelationServiceBukkit(repo, memberService, guildRepository)
    }

    private fun truce(status: RelationStatus, updatedAt: Instant = Instant.now()): Relation =
        Relation.create(
            guildA = guildA, guildB = guildB,
            type = RelationType.TRUCE, status = status,
            expiresAt = Instant.now().plus(Duration.ofDays(1)),
            requestingGuildId = guildA
        ).copy(updatedAt = updatedAt)

    private fun ally(status: RelationStatus): Relation =
        Relation.create(guildA = guildA, guildB = guildB, type = RelationType.ALLY, status = status)

    private fun enemy(): Relation =
        Relation.create(guildA = guildA, guildB = guildB, type = RelationType.ENEMY, status = RelationStatus.ACTIVE)

    @Test
    fun `stale rejected alliance row is reused, not blocked`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        every { repo.getByGuilds(any(), any()) } returns ally(RelationStatus.REJECTED)
        every { repo.update(any()) } returns true

        val result = service(repo).requestAlliance(guildA, guildB, actorId)

        assertNotNull(result)
        val saved = slot<Relation>()
        verify { repo.update(capture(saved)) }
        assertEquals(RelationType.ALLY, saved.captured.type)
        assertEquals(RelationStatus.PENDING, saved.captured.status)
    }

    @Test
    fun `pending alliance request blocks a new alliance request`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        every { repo.getByGuilds(any(), any()) } returns ally(RelationStatus.PENDING)

        assertNull(service(repo).requestAlliance(guildA, guildB, actorId))
        verify(exactly = 0) { repo.update(any()) }
        verify(exactly = 0) { repo.add(any()) }
    }

    @Test
    fun `rejecting a pending truce reverts to active enemy`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        val pending = truce(RelationStatus.PENDING)
        every { repo.getById(pending.id) } returns pending
        every { repo.update(any()) } returns true

        assertTrue(service(repo).rejectRequest(pending.id, guildB, actorId))

        val saved = slot<Relation>()
        verify { repo.update(capture(saved)) }
        assertEquals(RelationType.ENEMY, saved.captured.type)
        assertEquals(RelationStatus.ACTIVE, saved.captured.status)
        assertNull(saved.captured.expiresAt)
    }

    @Test
    fun `cancelling a pending truce reverts to active enemy, not removed`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        val pending = truce(RelationStatus.PENDING)
        every { repo.getById(pending.id) } returns pending
        every { repo.update(any()) } returns true

        assertTrue(service(repo).cancelRequest(pending.id, guildA, actorId))

        verify(exactly = 0) { repo.remove(any()) }
        val saved = slot<Relation>()
        verify { repo.update(capture(saved)) }
        assertEquals(RelationType.ENEMY, saved.captured.type)
    }

    @Test
    fun `rejecting a pending alliance removes the row`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        val pending = ally(RelationStatus.PENDING)
        every { repo.getById(pending.id) } returns pending
        every { repo.remove(pending.id) } returns true

        assertTrue(service(repo).rejectRequest(pending.id, guildB, actorId))
        verify { repo.remove(pending.id) }
    }

    @Test
    fun `pending truce reads as enemy until accepted`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        every { repo.getByGuilds(any(), any()) } returns truce(RelationStatus.PENDING)
        assertEquals(RelationType.ENEMY, service(repo).getRelationType(guildA, guildB))
    }

    @Test
    fun `rejected row reads as neutral`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        every { repo.getByGuilds(any(), any()) } returns ally(RelationStatus.REJECTED)
        assertEquals(RelationType.NEUTRAL, service(repo).getRelationType(guildA, guildB))
    }

    @Test
    fun `cleanup removes terminal rows and resolves long-pending requests`() {
        val repo = mockk<RelationRepository>(relaxed = true)
        val rejected = ally(RelationStatus.REJECTED)
        val stalePending = truce(RelationStatus.PENDING, updatedAt = Instant.now().minus(Duration.ofDays(30)))
        every { repo.getByStatus(RelationStatus.REJECTED) } returns setOf(rejected)
        every { repo.getByStatus(RelationStatus.EXPIRED) } returns emptySet()
        every { repo.getByStatus(RelationStatus.PENDING) } returns setOf(stalePending)
        every { repo.remove(rejected.id) } returns true
        every { repo.update(any()) } returns true

        val cleaned = service(repo).cleanupStaleRelations()

        assertEquals(2, cleaned)
        verify { repo.remove(rejected.id) }
        // stale pending truce reverted to enemy
        val saved = slot<Relation>()
        verify { repo.update(capture(saved)) }
        assertEquals(RelationType.ENEMY, saved.captured.type)
    }
}
