package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.PeriodWindow

interface ExperienceAwardRepository {
    fun awardAtomically(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult
}
