package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-155: cached per-guild prestige standing, refreshed on TTL and on prestige. */
class GuildPrestigeStandingsTest {
    private val guild = UUID.randomUUID()
    private val rival = UUID.randomUUID()
    private val owners = mockk<RewardOwnershipRepository>()
    private val history = mockk<PrestigeHistory>()
    private var now = Instant.parse("2026-11-04T00:00:00Z")
    private val t1 = Instant.parse("2026-10-06T12:00:00Z")
    private val t2 = Instant.parse("2026-10-09T12:00:00Z")

    private fun found(tier: Int) = RewardOwnershipRead.Found(RewardOwnershipSnapshot(1, RewardOwnership(prestigeCount = tier)))
    private fun record(g: UUID, tier: Int, at: Instant) = PrestigeRecord(UUID.randomUUID(), g, UUID.randomUUID(), tier, "bank-1", at)
    private fun subject() = GuildPrestigeStandings(owners, history, Duration.ofSeconds(30)) { now }

    private fun seed(tier: Int = 2) {
        every { owners.read(guild) } returns found(tier)
        every { history.history(guild) } returns listOf(record(guild, 1, t1), record(guild, 2, t2))
        every { history.firstToReach(1) } returns record(guild, 1, t1)
        every { history.firstToReach(2) } returns record(rival, 2, t1)
        (3..6).forEach { every { history.firstToReach(it) } returns null }
    }

    @Test
    fun `combines ownership tier, latest history date and highest first-reach tier`() {
        seed()
        assertEquals(PrestigeStanding(tier = 2, lastAppliedAt = t2, firstTier = 1), subject().standing(guild))
    }

    @Test
    fun `serves the cached standing within the TTL and rereads after it`() {
        seed()
        val subject = subject()
        subject.standing(guild); subject.standing(guild)
        verify(exactly = 1) { owners.read(guild) }
        verify(exactly = 1) { history.firstToReach(1) }
        now = now.plusSeconds(31)
        subject.standing(guild)
        verify(exactly = 2) { owners.read(guild) }
        verify(exactly = 2) { history.firstToReach(1) }
    }

    @Test
    fun `a newly committed prestige refreshes that guild and the first holders immediately`() {
        seed(tier = 2)
        val subject = subject()
        assertEquals(2, subject.standing(guild)?.tier)
        every { owners.read(guild) } returns found(3)
        every { history.firstToReach(3) } returns record(guild, 3, now)
        subject.prestiged(PrestigeCompletion(UUID.randomUUID(), guild, UUID.randomUUID(), 3, "bank-1", now, true))
        assertEquals(PrestigeStanding(3, t2, 3), subject.standing(guild))
    }

    @Test
    fun `a guild without a reward account is tier zero and a failed read is unavailable`() {
        every { owners.read(guild) } returns RewardOwnershipRead.Missing
        every { history.history(guild) } returns emptyList()
        (1..6).forEach { every { history.firstToReach(it) } returns null }
        assertEquals(PrestigeStanding(0, null, 0), subject().standing(guild))

        every { owners.read(rival) } returns RewardOwnershipRead.Failed("db down")
        every { history.history(rival) } returns emptyList()
        assertNull(subject().standing(rival))
    }

    @Test
    fun `a history failure makes the standing unavailable rather than wrong`() {
        every { owners.read(guild) } returns found(1)
        every { history.history(guild) } throws IllegalStateException("db down")
        (1..6).forEach { every { history.firstToReach(it) } returns null }
        assertNull(subject().standing(guild))
    }
}
