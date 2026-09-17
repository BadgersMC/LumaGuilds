package net.lumalyte.lg.domain.values

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Collections

/** Immutable UTC schedule; applies before cap reservation without changing the source policy. */
class ExperienceBoost(
    val startsAt: Instant,
    val endsAt: Instant,
    val multiplier: Double,
    sources: Set<ExperienceSource>
) {
    val sources: Set<ExperienceSource> = Collections.unmodifiableSet(sources.toSet())

    init {
        require(startsAt < endsAt) { "XP boost end must follow its start" }
        require(multiplier.isFinite() && multiplier >= 1.0) { "XP boost multiplier must be finite and at least one" }
        require(sources.isNotEmpty()) { "XP boost needs at least one source" }
    }

    fun apply(baseXp: Int, source: ExperienceSource, occurredAt: Instant): Int {
        require(baseXp > 0)
        if (source !in sources || occurredAt < startsAt || occurredAt >= endsAt) return baseXp
        return BigDecimal.valueOf(baseXp.toLong()).multiply(BigDecimal.valueOf(multiplier))
            .setScale(0, RoundingMode.FLOOR).intValueExact()
    }
}
