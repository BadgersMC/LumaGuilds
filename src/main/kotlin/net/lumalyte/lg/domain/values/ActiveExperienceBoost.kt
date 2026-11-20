package net.lumalyte.lg.domain.values

import net.lumalyte.lg.config.WeekendBoostConfig
import java.time.Instant

/**
 * REQ-178: the XP boost in force at [now]. A weekend day becomes a one-day boost
 * window, so everything that already honours the dated boost (awards, bank
 * deposit quotes) honours the weekend too. The two never stack: the larger wins.
 */
object ActiveExperienceBoost {
    /** Quest, advancement and admin XP keep their exact amounts. */
    private val WEEKEND_SOURCES = ExperienceSource.entries
        .filterNot { it == ExperienceSource.WEEKLY_ACTIVITY || it == ExperienceSource.ADMIN_BONUS || it == ExperienceSource.GUILD_ADVANCEMENT }
        .toSet()

    fun at(now: Instant, dated: ExperienceBoost?, weekend: WeekendBoostConfig): ExperienceBoost? {
        val datedNow = dated?.takeIf { now >= it.startsAt && now < it.endsAt }
        val weekendNow = weekendWindow(now, weekend)
        return when {
            datedNow == null -> weekendNow ?: dated
            weekendNow == null -> datedNow
            else -> maxOf(datedNow, weekendNow, compareBy { it.multiplier })
        }
    }

    private fun weekendWindow(now: Instant, weekend: WeekendBoostConfig): ExperienceBoost? {
        if (!weekend.enabled || weekend.multiplier <= 1.0) return null
        val day = now.atZone(weekend.zone).toLocalDate()
        if (day.dayOfWeek !in weekend.days) return null
        return ExperienceBoost(
            day.atStartOfDay(weekend.zone).toInstant(),
            day.plusDays(1).atStartOfDay(weekend.zone).toInstant(),
            weekend.multiplier,
            WEEKEND_SOURCES,
        )
    }
}
