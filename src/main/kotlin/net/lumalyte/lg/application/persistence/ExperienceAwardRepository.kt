package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.PeriodWindow
import java.util.UUID

interface ExperienceAwardRepository {
    fun awardAtomically(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult

    /** Reads authoritative active cap usage for one guild, keyed by shared pool. */
    fun getAwardedXpByPool(guildId: UUID, at: java.time.Instant): Map<String, Int> = emptyMap()
}
