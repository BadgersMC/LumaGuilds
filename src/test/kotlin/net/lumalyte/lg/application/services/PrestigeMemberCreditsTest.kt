package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.PrestigeCreditLedger
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-154: tenure-gated, once-per-pair console credits for online members. */
class PrestigeMemberCreditsTest {
    private val guild = UUID.randomUUID()
    private val veteran = UUID.randomUUID()
    private val recruit = UUID.randomUUID()
    private val day = Duration.ofHours(24)
    private val t0 = Instant.parse("2026-10-01T00:00:00Z")
    private val tierOne = PrestigeRecord(UUID.randomUUID(), guild, veteran, 1, "bank-1", t0.plus(Duration.ofDays(10)))
    private val tierTwo = PrestigeRecord(UUID.randomUUID(), guild, veteran, 2, "travel-50", t0.plus(Duration.ofDays(20)))
    private val members = mockk<MemberRepository>()
    private val history = mockk<PrestigeHistory>()
    private val dispatched = mutableListOf<String>()
    private val ledger = object : PrestigeCreditLedger {
        val rows = mutableSetOf<Pair<UUID, UUID>>()
        override fun delivered(playerId: UUID) = rows.filter { it.first == playerId }.map { it.second }.toSet()
        override fun record(playerId: UUID, transactionId: UUID, at: Instant) = rows.add(playerId to transactionId)
    }
    private var settings = PrestigeMemberCreditSettings(
        enabled = true,
        minTenure = day,
        commands = listOf("enthusiatags rewards counter {uuid} guild_prestige_{tier} 1"),
        firstReachCommands = listOf("enthusiatags rewards counter {uuid} guild_prestige_first_{tier} 1", "say {player} first {tier}"),
    )

    private fun member(id: UUID, joined: Instant) = Member(id, guild, UUID.randomUUID(), joined)

    private fun subject() = PrestigeMemberCredits(members, history, ledger, { settings }, { t0.plus(Duration.ofDays(30)) }) { dispatched += it }

    private fun seed(first: PrestigeRecord? = null) {
        every { members.getGuildsByPlayer(veteran) } returns setOf(guild)
        every { members.getGuildsByPlayer(recruit) } returns setOf(guild)
        every { members.getByPlayerAndGuild(veteran, guild) } returns member(veteran, t0)
        every { members.getByPlayerAndGuild(recruit, guild) } returns member(recruit, tierTwo.appliedAt.minus(Duration.ofHours(2)))
        every { members.getByGuild(guild) } returns setOf(member(veteran, t0), member(recruit, tierTwo.appliedAt.minus(Duration.ofHours(2))))
        every { history.history(guild) } returns listOf(tierOne, tierTwo)
        every { history.firstToReach(1) } returns (first ?: tierOne.copy(transactionId = UUID.randomUUID()))
        every { history.firstToReach(2) } returns tierTwo.copy(transactionId = UUID.randomUUID())
    }

    @Test
    fun `veteran receives every owed tier with placeholders and nothing is repeated`() {
        seed()
        subject().deliver(veteran, "Steve")
        subject().deliver(veteran, "Steve")
        assertEquals(
            listOf(
                "enthusiatags rewards counter $veteran guild_prestige_1 1",
                "enthusiatags rewards counter $veteran guild_prestige_2 1",
            ),
            dispatched,
        )
        assertEquals(setOf(tierOne.transactionId, tierTwo.transactionId), ledger.delivered(veteran))
    }

    @Test
    fun `members inside the tenure window are not credited for that prestige`() {
        seed()
        subject().deliver(recruit, "Alex")
        assertTrue(dispatched.isEmpty())
        assertTrue(ledger.delivered(recruit).isEmpty())
    }

    @Test
    fun `first guild to reach a tier also runs the first-reach commands`() {
        seed(first = tierOne)
        subject().deliver(veteran, "Steve")
        assertEquals(
            listOf(
                "enthusiatags rewards counter $veteran guild_prestige_1 1",
                "enthusiatags rewards counter $veteran guild_prestige_first_1 1",
                "say Steve first 1",
                "enthusiatags rewards counter $veteran guild_prestige_2 1",
            ),
            dispatched,
        )
    }

    @Test
    fun `disabled credits dispatch and record nothing`() {
        seed()
        settings = settings.copy(enabled = false)
        subject().deliver(veteran, "Steve")
        subject().deliverToOnlineMembers(guild) { "Steve" }
        assertTrue(dispatched.isEmpty())
        assertTrue(ledger.rows.isEmpty())
    }

    @Test
    fun `a failed dispatch is not recorded so the next join retries it`() {
        seed()
        var fail = true
        val flaky = PrestigeMemberCredits(members, history, ledger, { settings }, { t0.plus(Duration.ofDays(30)) }) {
            if (fail) error("console unavailable") else dispatched += it
        }
        flaky.deliver(veteran, "Steve")
        assertTrue(ledger.delivered(veteran).isEmpty())
        fail = false
        flaky.deliver(veteran, "Steve")
        assertEquals(2, dispatched.size)
    }

    @Test
    fun `after a prestige only online members are credited`() {
        seed()
        subject().deliverToOnlineMembers(guild) { id -> if (id == veteran) "Steve" else null }
        assertEquals(2, dispatched.size)
        assertTrue(ledger.delivered(recruit).isEmpty())
    }

    @Test
    fun `players no longer in the guild receive nothing`() {
        every { members.getGuildsByPlayer(veteran) } returns emptySet()
        subject().deliver(veteran, "Steve")
        assertTrue(dispatched.isEmpty())
    }
}
