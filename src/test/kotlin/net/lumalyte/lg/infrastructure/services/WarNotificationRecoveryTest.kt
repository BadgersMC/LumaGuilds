package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.WarNotificationRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.domain.entities.DurableWarRecord
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarNotification
import net.lumalyte.lg.domain.entities.WarNotificationKind
import net.lumalyte.lg.domain.entities.WarStats
import net.lumalyte.lg.domain.entities.WarStatus
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class WarNotificationRecoveryTest {
    private val playerId = UUID.randomUUID()
    private val declaringGuildId = UUID.randomUUID()
    private val defendingGuildId = UUID.randomUUID()
    private val rankId = UUID.randomUUID()
    private val eventAt = Instant.parse("2026-09-23T12:00:00Z")

    private val notifications = mockk<WarNotificationRepository>()
    private val guilds = mockk<GuildRepository>()
    private val members = mockk<MemberRepository>(relaxed = true)
    private val wars = mockk<WarRepository>(relaxed = true).also {
        every { it.save(any()) } returns true
    }

    private fun subject() = WarNotificationServiceBukkit(
        plugin = mockk<Plugin>(relaxed = true),
        repository = notifications,
        guildRepository = guilds,
        memberRepository = members,
        warRepository = wars,
        lang = mockk<LangService>(relaxed = true),
        toastSender = mockk<ToastSender>(relaxed = true),
    )

    private fun member(guildId: UUID, joinedAt: Instant = eventAt.minusSeconds(60)) =
        Member(playerId, guildId, rankId, joinedAt)

    private fun membership(guildId: UUID, joinedAt: Instant = eventAt.minusSeconds(60)) {
        every { members.getByGuild(guildId) } returns setOf(member(guildId, joinedAt))
    }

    private fun guild(id: UUID, name: String) =
        Guild(id = id, name = name, createdAt = eventAt.minusSeconds(3600))

    @Test
    fun `missing declaration notification is rebuilt from durable transition`() {
        val eventId = UUID.randomUUID()
        val declaration = WarDeclaration(
            id = eventId,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            proposedDuration = Duration.ofHours(12),
            terms = "No griefing",
            wagerAmount = 25,
            declaredAt = eventAt,
            expiresAt = eventAt.plusSeconds(3600),
        )
        membership(defendingGuildId)
        every { guilds.getById(declaringGuildId) } returns guild(declaringGuildId, "Raiders")
        every { wars.getAll() } returns listOf(
            DurableWarRecord(
                id = eventId,
                declaration = declaration,
                notificationRecipients = net.lumalyte.lg.domain.entities.WarNotificationRecipients(
                    declarationReceived = setOf(playerId),
                ),
                declarationNotificationExpected = true,
            )
        )
        val captured = slot<WarNotification>()
        every { notifications.add(capture(captured)) } returns true

        val recovered = subject().reconcileMissingNotifications()

        assertEquals(1, recovered)
        assertEquals(WarNotificationKind.DECLARATION_RECEIVED, captured.captured.kind)
        assertEquals(eventId, captured.captured.eventId)
        assertEquals(defendingGuildId, captured.captured.ownGuildId)
        assertEquals(declaringGuildId, captured.captured.opponentGuildId)
        assertEquals("Raiders", captured.captured.opponentName)
        assertEquals(25, captured.captured.wagerAmount)
        assertEquals("No griefing", captured.captured.terms)
        verify {
            wars.save(match { it.id == eventId && !it.declarationNotificationExpected })
        }
    }

    @Test
    fun `failed notification insert keeps durable recovery work for retry`() {
        val eventId = UUID.randomUUID()
        val declaration = WarDeclaration(
            id = eventId,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            declaredAt = eventAt,
            expiresAt = eventAt.plusSeconds(3600),
        )
        every { guilds.getById(declaringGuildId) } returns guild(declaringGuildId, "Raiders")
        val record = DurableWarRecord(
            id = eventId,
            declaration = declaration,
            notificationRecipients = net.lumalyte.lg.domain.entities.WarNotificationRecipients(
                declarationReceived = setOf(playerId),
            ),
            declarationNotificationExpected = true,
        )
        every { wars.getAll() } returns listOf(record)
        every { notifications.add(any()) } throws java.sql.SQLException("simulated insert outage") andThen true

        val service = subject()
        kotlin.test.assertFailsWith<java.sql.SQLException> {
            service.reconcileMissingNotifications()
        }
        verify(exactly = 0) { wars.save(any()) }

        assertEquals(1, service.reconcileMissingNotifications())
        verify {
            wars.save(match { it.id == eventId && !it.declarationNotificationExpected })
        }
    }

    @Test
    fun `legacy records without recovery marker are not backfilled`() {
        val eventId = UUID.randomUUID()
        val declaration = WarDeclaration(
            id = eventId,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            declaredAt = eventAt,
            expiresAt = eventAt.plusSeconds(3600),
        )
        membership(defendingGuildId)
        every { wars.getAll() } returns listOf(
            DurableWarRecord(id = eventId, declaration = declaration)
        )

        val recovered = subject().reconcileMissingNotifications()

        assertEquals(0, recovered)
        verify(exactly = 0) { notifications.add(any()) }
    }

    @Test
    fun `snapshotted member is recovered after leaving guild`() {
        val eventId = UUID.randomUUID()
        val declaration = WarDeclaration(
            id = eventId,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            declaredAt = eventAt,
            expiresAt = eventAt.plusSeconds(3600),
        )
        every { members.getByGuild(defendingGuildId) } returns emptySet()
        every { guilds.getById(declaringGuildId) } returns guild(declaringGuildId, "Raiders")
        every { wars.getAll() } returns listOf(
            DurableWarRecord(
                id = eventId,
                declaration = declaration,
                notificationRecipients = net.lumalyte.lg.domain.entities.WarNotificationRecipients(
                    declarationReceived = setOf(playerId),
                ),
                declarationNotificationExpected = true,
            )
        )
        val captured = slot<WarNotification>()
        every { notifications.add(capture(captured)) } returns true

        val recovered = subject().reconcileMissingNotifications()

        assertEquals(1, recovered)
        assertEquals(playerId, captured.captured.playerId)
        verify(exactly = 0) { members.getByGuild(defendingGuildId) }
    }

    @Test
    fun `accepted and resolved rows recover idempotently`() {
        val eventId = UUID.randomUUID()
        val endedAt = eventAt.plusSeconds(600)
        val war = War(
            id = eventId,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            startedAt = eventAt,
            endedAt = endedAt,
            duration = Duration.ofHours(6),
            status = WarStatus.ENDED,
            winner = declaringGuildId,
            loser = defendingGuildId,
        )
        membership(declaringGuildId)
        every { guilds.getById(defendingGuildId) } returns guild(defendingGuildId, "Defenders")
        every { wars.getAll() } returns listOf(
            DurableWarRecord(
                id = eventId,
                war = war,
                notificationRecipients = net.lumalyte.lg.domain.entities.WarNotificationRecipients(
                    acceptanceDeclaring = setOf(playerId),
                    victory = setOf(playerId),
                ),
                stats = WarStats(
                    warId = eventId,
                    declaringGuildKills = 10,
                    defendingGuildKills = 4,
                    lastUpdated = endedAt,
                ),
                acceptanceNotificationExpected = true,
                resolutionNotificationExpected = true,
            )
        )
        val captured = mutableListOf<WarNotification>()
        every { notifications.add(capture(captured)) } returnsMany listOf(true, true, false, false)

        val service = subject()
        assertEquals(2, service.reconcileMissingNotifications())
        assertEquals(0, service.reconcileMissingNotifications())

        assertEquals(
            listOf(
                WarNotificationKind.WAR_ACCEPTED,
                WarNotificationKind.VICTORY,
                WarNotificationKind.WAR_ACCEPTED,
                WarNotificationKind.VICTORY,
            ),
            captured.map { it.kind },
        )
        assertEquals(captured[0].id, captured[2].id)
        assertEquals(captured[1].id, captured[3].id)
        assertEquals(10, captured[1].ownKills)
        assertEquals(4, captured[1].opponentKills)
        assertTrue(captured.all { it.playerId == playerId })
        verify(atLeast = 1) {
            wars.save(match {
                it.id == eventId &&
                    !it.acceptanceNotificationExpected &&
                    !it.resolutionNotificationExpected
            })
        }
    }
}
