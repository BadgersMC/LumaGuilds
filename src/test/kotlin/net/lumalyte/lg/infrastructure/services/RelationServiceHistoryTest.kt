package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.DiplomacyLog
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-203: relation changes reach the diplomacy log; breaking an alliance goes through the service. */
class RelationServiceHistoryTest {
    private val a: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val b: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val actor = UUID.randomUUID()
    private val repo = mockk<RelationRepository>(relaxed = true)
    private val log = mockk<DiplomacyLog>(relaxed = true)
    private val members = mockk<MemberService>(relaxed = true)

    private fun service(canManage: Boolean = true): RelationServiceBukkit {
        every { members.getPlayerGuilds(any()) } returns setOf(a, b)
        every { members.hasPermission(any(), any(), RankPermission.MANAGE_RELATIONS) } returns canManage
        every { repo.update(any()) } returns true
        every { repo.remove(any()) } returns true
        return RelationServiceBukkit(repo, members, mockk<GuildRepository>(relaxed = true), log)
    }

    @Test
    fun `accepting an alliance is logged`() {
        val pending = Relation.create(guildA = a, guildB = b, type = RelationType.ALLY, status = RelationStatus.PENDING, requestingGuildId = a)
        every { repo.getById(pending.id) } returns pending
        service().acceptAlliance(pending.id, b, actor)
        verify { log.allied(a, b, actor) }
    }

    @Test
    fun `accepting a truce logs its length`() {
        val pending = Relation.create(
            guildA = a, guildB = b, type = RelationType.TRUCE, status = RelationStatus.PENDING,
            expiresAt = Instant.now().plus(Duration.ofDays(3)).plusSeconds(30), requestingGuildId = a,
        )
        every { repo.getById(pending.id) } returns pending
        service().acceptTruce(pending.id, b, actor)
        verify { log.truceStarted(a, b, actor, 3) }
    }

    @Test
    fun `breaking an alliance removes it and logs it`() {
        val ally = Relation.create(guildA = a, guildB = b, type = RelationType.ALLY, status = RelationStatus.ACTIVE)
        every { repo.getById(ally.id) } returns ally
        assertTrue(service().breakAlliance(ally.id, a, actor))
        verify { repo.remove(ally.id) }
        verify { log.allianceEnded(a, b, actor) }
    }

    @Test
    fun `breaking needs Manage Relations and an active alliance`() {
        val ally = Relation.create(guildA = a, guildB = b, type = RelationType.ALLY, status = RelationStatus.ACTIVE)
        every { repo.getById(ally.id) } returns ally
        assertFalse(service(canManage = false).breakAlliance(ally.id, a, actor))
        val enemy = Relation.create(guildA = a, guildB = b, type = RelationType.ENEMY, status = RelationStatus.ACTIVE)
        every { repo.getById(enemy.id) } returns enemy
        assertFalse(service().breakAlliance(enemy.id, a, actor))
        verify(exactly = 0) { repo.remove(any()) }
    }
}
