package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.AwardRejection
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy

class PermanentExperienceService(
    private val repository: ExperienceAwardRepository,
    private val activityService: PlaytimeActivityService,
) {
    fun award(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
    ): ExperienceAwardResult {
        if (!policy.enabled) {
            return ExperienceAwardResult.Rejected(AwardRejection.SOURCE_DISABLED)
        }
        if (!request.eligible) {
            return ExperienceAwardResult.Rejected(AwardRejection.INELIGIBLE)
        }
        if (request.units <= 0) {
            return ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS)
        }
        if (request.actorId != null && activityService.isXpBlocked(request.actorId)) {
            return ExperienceAwardResult.Rejected(AwardRejection.SUSPICIOUS_OR_AFK)
        }

        val requestedXp = try {
            Math.multiplyExact(policy.awardXp, request.units)
        } catch (_: ArithmeticException) {
            return ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS)
        }
        return repository.awardAtomically(
            request = request,
            policy = policy,
            requestedXp = requestedXp,
            window = policy.windowContaining(request.occurredAt),
        )
    }
}
