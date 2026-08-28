package net.lumalyte.lg.domain.values

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

enum class CapPeriod {
    DAILY,
    WEEKLY,
    UNLIMITED,
}

data class PeriodWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
) {
    init {
        require(startInclusive < endExclusive) { "Period window end must be after its start" }
    }
}

data class ExperiencePolicy(
    val source: ExperienceSource,
    val pool: String,
    val awardXp: Int,
    val capXp: Int,
    val period: CapPeriod,
    val enabled: Boolean,
) {
    init {
        require(pool.isNotBlank()) { "Experience pool must not be blank" }
        require(awardXp >= 0) { "Experience award must not be negative" }
        require(!enabled || awardXp > 0) { "Enabled experience policy must award positive XP" }
        require(capXp >= 0) { "Experience cap must not be negative" }
        require(period == CapPeriod.UNLIMITED || capXp > 0) {
            "Capped experience policy must have a positive cap"
        }
    }

    val isCapped: Boolean
        get() = period != CapPeriod.UNLIMITED

    fun windowContaining(instant: Instant): PeriodWindow? = when (period) {
        CapPeriod.UNLIMITED -> null
        CapPeriod.DAILY -> {
            val start = instant.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
            PeriodWindow(start, start.plusSeconds(86_400))
        }
        CapPeriod.WEEKLY -> {
            val startDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
            PeriodWindow(start, start.plusSeconds(7 * 86_400L))
        }
    }
}
