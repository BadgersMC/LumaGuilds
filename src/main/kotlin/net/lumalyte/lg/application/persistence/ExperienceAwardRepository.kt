package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PeriodWindow
import java.time.Instant
import java.util.UUID

interface ExperienceAwardRepository {
    fun awardAtomically(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult

    /** Reads authoritative active cap usage for one guild, keyed by shared pool. */
    fun getAwardedXpByPool(guildId: UUID, at: Instant): Map<String, Int> = emptyMap()

    /** Reads awarded XP grouped by concrete source inside an exact window. */
    fun getAwardedXpBySource(
        guildId: UUID,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Map<ExperienceSource, Int> = emptyMap()

    /** Reads total awarded XP inside an exact window, regardless of source cap period. */
    fun getAwardedXp(
        guildId: UUID,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Int = 0
}
