package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.AwardRejection
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource

class PermanentExperienceService(
    private val repository: ExperienceAwardRepository,
    private val activityService: PlaytimeActivityService,
    private val boostProvider: () -> net.lumalyte.lg.domain.values.ExperienceBoost? = { null },
) {
    /** Reject invalid/source-ineligible requests before reserving caps or changing progression. */
    fun award(request: ExperienceAwardRequest, policy: ExperiencePolicy): ExperienceAwardResult {
        val rejection = sourceRejection(request, policy) ?: actorRejection(request)
        return if (rejection == null) awardEligible(request, policy) else ExperienceAwardResult.Rejected(rejection)
    }

    private fun sourceRejection(request: ExperienceAwardRequest, policy: ExperiencePolicy): AwardRejection? {
        return when {
            request.source == ExperienceSource.SHOP_SALE -> AwardRejection.INELIGIBLE
            request.source != policy.source -> AwardRejection.POLICY_MISMATCH
            !policy.enabled -> AwardRejection.SOURCE_DISABLED
            else -> null
        }
    }

    private fun actorRejection(request: ExperienceAwardRequest): AwardRejection? {
        return when {
            !request.eligible -> AwardRejection.INELIGIBLE
            request.units <= 0 -> AwardRejection.INVALID_UNITS
            request.actorId?.let(activityService::isXpBlocked) == true -> AwardRejection.SUSPICIOUS_OR_AFK
            else -> null
        }
    }

    private fun awardEligible(request: ExperienceAwardRequest, policy: ExperiencePolicy): ExperienceAwardResult {
        val requestedXp =
            requestedXp(request, policy) ?: return ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS)
        return repository.awardAtomically(request, policy, requestedXp, policy.windowContaining(request.occurredAt))
    }

    private fun requestedXp(request: ExperienceAwardRequest, policy: ExperiencePolicy): Int? {
        return try {
            val base = Math.multiplyExact(policy.awardXp, request.units)
            boostProvider()?.apply(base, request.source, request.occurredAt) ?: base
        } catch (_: ArithmeticException) {
            null
        }
    }
}
