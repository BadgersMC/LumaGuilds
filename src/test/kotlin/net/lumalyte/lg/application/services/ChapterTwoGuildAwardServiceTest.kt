package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.BankProgressionRepository
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.domain.entities.PreparedExperienceAward
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PeriodWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ChapterTwoGuildAwardServiceTest {
    private val now = Instant.parse("2026-08-28T12:00:00Z")
    private val awards = RecordingAwards()
    private val bank = InMemoryBankProgression(awards)
    private val history = InMemoryMembershipHistory()
    private val activity = RecordingActivity()
    private val config = MainConfig()
    private val committed = mutableListOf<Pair<UUID, Int?>>()
    private val service = ChapterTwoGuildAwardService(
        bank,
        history,
        PermanentExperienceService(awards, activity),
        object : ConfigService { override fun loadConfig() = config },
        activity,
        onCommittedExperience = { guildId, level -> committed += guildId to level },
    )

    @Test
    fun `withdraw and redeposit awards only first net bank growth`() {
        val guildId = UUID.randomUUID()
        val actorId = UUID.randomUUID()

        service.awardBankGrowth(guildId, actorId, 0, 10_000, now)
        service.awardBankGrowth(guildId, actorId, 10_000, 0, now)
        service.awardBankGrowth(guildId, actorId, 0, 10_000, now)

        assertEquals(listOf(100), awards.calls.map { it.requestedXp })
        assertEquals(ExperienceSource.BANK_DEPOSIT, awards.calls.single().request.source)
        assertEquals(listOf(guildId to null), committed)
    }

    /** Publishing a committed bank level does not issue a second award. */
    @Test
    fun bankLevelUpPublishesOnce() {
        val guildId = UUID.randomUUID()
        awards.leveledUpTo = 2

        service.awardBankGrowth(guildId, UUID.randomUUID(), 0, BANK_TEST_AMOUNT, now)

        assertEquals(listOf(guildId to 2), committed)
        assertEquals(1, awards.calls.size)
    }

    @Test
    fun `blocked bank actor does not consume high water`() {
        val guildId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        activity.blocked += actorId

        service.awardBankGrowth(guildId, actorId, 0, 10_000, now)
        assertEquals(emptyList<Pair<UUID, Int?>>(), committed)
        activity.blocked -= actorId
        service.awardBankGrowth(guildId, actorId, 0, 10_000, now)

        assertEquals(listOf(100), awards.calls.map { it.requestedXp })
    }

    @Test
    fun `retained recruit is awarded once after seven days`() {
        val stint = MembershipHistory(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(8 * 86_400L),
        )
        history.stints += stint

        assertEquals(1, service.processQualifiedRecruits(now))
        assertEquals(0, service.processQualifiedRecruits(now))
        assertEquals(listOf(1_000), awards.calls.map { it.requestedXp })
    }

    @Test
    fun `failed recruit marker retries without granting duplicate experience`() {
        val stint = MembershipHistory(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(8 * 86_400L),
        )
        history.stints += stint
        history.failNextMark = true

        assertEquals(0, service.processQualifiedRecruits(now))
        assertEquals(1, service.processQualifiedRecruits(now))
        assertEquals(listOf(1_000), awards.calls.map { it.requestedXp })
        assertEquals(now, history.stints.single().recruitXpAwardedAt)
    }

    @Test
    fun `war win awards below level one hundred only`() {
        val guildId = UUID.randomUUID()

        service.awardPreCapWarWin(guildId, 100, now)
        service.awardPreCapWarWin(guildId, 99, now)

        assertEquals(listOf(10_000), awards.calls.map { it.requestedXp })
        assertEquals(ExperienceSource.PRE_CAP_WAR_WIN, awards.calls.single().request.source)
    }

    private class RecordingAwards : ExperienceAwardRepository {
        var leveledUpTo: Int? = null
        data class Call(val request: ExperienceAwardRequest, val requestedXp: Int)
        val calls = mutableListOf<Call>()
        private val transactionIds = mutableSetOf<UUID>()
        override fun awardAtomically(
            request: ExperienceAwardRequest,
            policy: ExperiencePolicy,
            requestedXp: Int,
            window: PeriodWindow?,
        ): ExperienceAwardResult {
            if (!transactionIds.add(request.transactionId)) return ExperienceAwardResult.Duplicate
            calls += Call(request, requestedXp)
            return ExperienceAwardResult.Awarded(requestedXp, requestedXp, policy.isCapped, leveledUpTo)
        }
    }

    private class RecordingActivity : PlaytimeActivityService {
        val blocked = mutableSetOf<UUID>()
        override fun isXpBlocked(playerId: UUID) = playerId in blocked
    }

    private companion object {
        const val BANK_TEST_AMOUNT = 100L
    }

    private class InMemoryBankProgression(private val awards: ExperienceAwardRepository) : BankProgressionRepository {
        private val highs = mutableMapOf<Pair<UUID, Instant>, Long>()
        override fun awardNetNewUnits(
            guildId: UUID,
            openingBalance: Long,
            currentBalance: Long,
            valuePerUnit: Long,
            window: PeriodWindow,
            prepareAward: (Int) -> PreparedExperienceAward,
        ): ExperienceAwardResult? {
            val units = reserveNetNewUnits(guildId, openingBalance, currentBalance, valuePerUnit, window)
            if (units <= 0) return null
            return when (val prepared = prepareAward(units)) {
                is PreparedExperienceAward.Rejected -> ExperienceAwardResult.Rejected(prepared.reason)
                is PreparedExperienceAward.Ready -> awards.awardAtomically(
                    prepared.request, prepared.policy, prepared.requestedXp, window,
                )
            }
        }

        override fun reserveNetNewUnits(
            guildId: UUID,
            openingBalance: Long,
            currentBalance: Long,
            valuePerUnit: Long,
            window: PeriodWindow,
        ): Int {
            val key = guildId to window.startInclusive
            val previous = highs[key] ?: openingBalance
            val units = ChapterTwoGuildAwardRules.netNewBankUnits(previous, currentBalance, valuePerUnit)
            highs[key] = maxOf(previous, currentBalance)
            return units
        }
    }

    private class InMemoryMembershipHistory : MembershipHistoryRepository {
        val stints = mutableListOf<MembershipHistory>()
        var failNextMark = false
        override fun openStint(playerId: UUID, guildId: UUID) = false
        override fun closeStint(playerId: UUID, guildId: UUID, reason: DepartureReason) = false
        override fun getByPlayer(playerId: UUID) = stints.filter { it.playerId == playerId }
        override fun getQualifiedUnawarded(joinedAtOrBefore: Instant) = stints.filter {
            it.departedAt == null && it.recruitXpAwardedAt == null && !it.joinedAt.isAfter(joinedAtOrBefore)
        }
        override fun markRecruitXpAwarded(stintId: UUID, awardedAt: Instant): Boolean {
            if (failNextMark) {
                failNextMark = false
                return false
            }
            val index = stints.indexOfFirst { it.id == stintId && it.recruitXpAwardedAt == null }
            if (index < 0) return false
            stints[index] = stints[index].copy(recruitXpAwardedAt = awardedAt)
            return true
        }
    }
}
