package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.AwardRejection
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.PreparedExperienceAward
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperienceBoost
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PeriodWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-089 proves every rejection occurs before repository/cap accounting. */
class PermanentExperienceServiceTest {

    private val guildId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val actorId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val instant = Instant.parse("2026-08-28T12:00:00Z")
    private val repository = RecordingAwardRepository()
    private val activity = RecordingActivityService()
    private val service = PermanentExperienceService(repository, activity)
    private val mobPolicy = ExperiencePolicy(
        ExperienceSource.MOB_KILL, "MOB_KILL", 2, 6_000, CapPeriod.DAILY, true,
    )
    private val request = ExperienceAwardRequest(
        guildId, actorId, ExperienceSource.MOB_KILL, 1, instant, eligible = true,
    )

    @Test
    fun `ineligible activity is rejected before cap accounting`() {
        val result = service.award(request.copy(eligible = false), mobPolicy)

        assertEquals(ExperienceAwardResult.Rejected(AwardRejection.INELIGIBLE), result)
        assertNull(repository.lastCall)
        assertEquals(emptyList<UUID>(), activity.checkedPlayers)
    }

    @Test
    fun `AFK or suspicious actor is rejected before cap accounting`() {
        activity.blockedPlayers += actorId

        val result = service.award(request, mobPolicy)

        assertEquals(ExperienceAwardResult.Rejected(AwardRejection.SUSPICIOUS_OR_AFK), result)
        assertNull(repository.lastCall)
        assertEquals(listOf(actorId), activity.checkedPlayers)
    }

    @Test
    fun `eligible units become configured XP inside the UTC cap window`() {
        val result = service.award(request.copy(units = 3), mobPolicy)

        assertEquals(ExperienceAwardResult.Awarded(6, 6, true), result)
        assertEquals(6, repository.lastCall?.requestedXp)
        assertEquals(
            PeriodWindow(
                Instant.parse("2026-08-28T00:00:00Z"),
                Instant.parse("2026-08-29T00:00:00Z"),
            ),
            repository.lastCall?.window,
        )
    }

    @Test
    fun `weekly quest award bypasses actor check and cap window`() {
        val weeklyPolicy = ExperiencePolicy(
            ExperienceSource.WEEKLY_ACTIVITY,
            "WEEKLY_ACTIVITY",
            1,
            0,
            CapPeriod.UNLIMITED,
            true,
        )
        val weeklyRequest = ExperienceAwardRequest(
            guildId, null, ExperienceSource.WEEKLY_ACTIVITY, 25_000, instant, eligible = true,
        )

        val result = service.award(weeklyRequest, weeklyPolicy)

        assertEquals(ExperienceAwardResult.Awarded(25_000, 25_000, false), result)
        assertEquals(25_000, repository.lastCall?.requestedXp)
        assertNull(repository.lastCall?.window)
        assertEquals(emptyList<UUID>(), activity.checkedPlayers)
    }

    @Test
    fun `disabled source and invalid units never reach repository`() {
        val disabled = mobPolicy.copy(enabled = false, awardXp = 0)

        assertEquals(
            ExperienceAwardResult.Rejected(AwardRejection.SOURCE_DISABLED),
            service.award(request, disabled),
        )
        assertEquals(
            ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS),
            service.award(request.copy(units = 0), mobPolicy),
        )
        assertNull(repository.lastCall)
    }

    @Test
    fun `mismatched request and policy sources never reach repository`() {
        val result = service.award(request.copy(source = ExperienceSource.DIAMOND_ORE), mobPolicy)

        assertEquals(ExperienceAwardResult.Rejected(AwardRejection.POLICY_MISMATCH), result)
        assertNull(repository.lastCall)
    }

    @Test
    fun `overflowing award is rejected before repository`() {
        val result = service.award(request.copy(units = Int.MAX_VALUE), mobPolicy)

        assertEquals(ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS), result)
        assertNull(repository.lastCall)
    }

    @Test fun preparedUnitsUseCapturedEligibilityAndWholeAwardBoost() {
        var boost: ExperienceBoost? = ExperienceBoost(instant, instant.plusSeconds(60), 1.5, setOf(request.source))
        var boostReads = 0
        val preparing = PermanentExperienceService(repository, activity) { boostReads++; boost }
        val singleXp = mobPolicy.copy(awardXp = 1)
        val calculate = preparing.prepareForUnits(request, singleXp)
        activity.blockedPlayers += actorId
        boost = null

        val single = calculate(1) as PreparedExperienceAward.Ready
        val combined = calculate(2) as PreparedExperienceAward.Ready

        assertEquals(1, single.requestedXp)
        assertEquals(3, combined.requestedXp)
        assertEquals(2, combined.request.units)
        assertEquals(1, boostReads)
        assertEquals(listOf(actorId), activity.checkedPlayers)
        assertNull(repository.lastCall)
    }

    @Test fun preparedRejectionDoesNotReadBoostOrWriteXp() {
        val preparing = PermanentExperienceService(repository, activity) { error("Rejected source must not read boost") }
        val calculate = preparing.prepareForUnits(request, mobPolicy.copy(enabled = false, awardXp = 0))

        assertEquals(PreparedExperienceAward.Rejected(AwardRejection.SOURCE_DISABLED), calculate(2))
        assertEquals(emptyList<UUID>(), activity.checkedPlayers)
        assertNull(repository.lastCall)
    }

    @Test fun preparedInvalidUnitsAndOverflowNeverWriteXp() {
        val calculate = service.prepareForUnits(request, mobPolicy)

        assertEquals(PreparedExperienceAward.Rejected(AwardRejection.INVALID_UNITS), calculate(0))
        assertEquals(PreparedExperienceAward.Rejected(AwardRejection.INVALID_UNITS), calculate(Int.MAX_VALUE))
        assertNull(repository.lastCall)
    }

    @Test fun ordinaryOverflowDoesNotResolveBoost() {
        val awarding = PermanentExperienceService(repository, activity) { error("Overflow must precede boost lookup") }

        assertEquals(
            ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS),
            awarding.award(request.copy(units = Int.MAX_VALUE), mobPolicy),
        )
        assertNull(repository.lastCall)
    }

    @Test fun ordinaryBoostArithmeticFailureRetainsRejection() {
        val awarding = PermanentExperienceService(repository, activity) { throw ArithmeticException("boost unavailable") }

        assertEquals(
            ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS),
            awarding.award(request, mobPolicy),
        )
        assertNull(repository.lastCall)
    }

    private class RecordingActivityService : PlaytimeActivityService {
        val blockedPlayers = mutableSetOf<UUID>()
        val checkedPlayers = mutableListOf<UUID>()

        override fun isXpBlocked(playerId: UUID): Boolean {
            checkedPlayers += playerId
            return playerId in blockedPlayers
        }
    }

    private class RecordingAwardRepository : ExperienceAwardRepository {
        data class Call(
            val request: ExperienceAwardRequest,
            val policy: ExperiencePolicy,
            val requestedXp: Int,
            val window: PeriodWindow?,
        )

        var lastCall: Call? = null

        override fun awardAtomically(
            request: ExperienceAwardRequest,
            policy: ExperiencePolicy,
            requestedXp: Int,
            window: PeriodWindow?,
        ): ExperienceAwardResult {
            lastCall = Call(request, policy, requestedXp, window)
            return ExperienceAwardResult.Awarded(requestedXp, requestedXp, policy.isCapped)
        }
    }
}
