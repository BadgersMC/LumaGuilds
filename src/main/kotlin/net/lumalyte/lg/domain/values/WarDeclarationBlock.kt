package net.lumalyte.lg.domain.values

import java.time.Duration
import java.time.Instant

/**
 * REQ-239: why a guild can't declare war right now, so the menu can say so
 * instead of "Failed to send war declaration".
 */
data class WarDeclarationBlock(val reason: Reason, val until: Instant? = null) {
    enum class Reason {
        /** The declaring guild declared a war recently. */
        DECLARATION_COOLDOWN,

        /** The declaring guild won a war recently. */
        OWN_FARMING_COOLDOWN,

        /** The target guild won a war recently. */
        TARGET_FARMING_COOLDOWN,

        /** The declaring guild is already in as many wars as it can be. */
        OWN_WAR_LIMIT,

        /** The target guild is already in as many wars as it can be. */
        TARGET_WAR_LIMIT,
    }

    /** Time left, rounded up to the minute; null when the block has no end time. */
    fun remaining(now: Instant): Duration? = until?.let {
        val left = Duration.between(now, it)
        if (left.isNegative || left.isZero) Duration.ZERO else Duration.ofMinutes((left.toMillis() + 59_999) / 60_000)
    }

    companion object {
        /** "1d 4h", "3h 20m", "12m". */
        fun format(left: Duration): String {
            val days = left.toDays()
            val hours = left.toHours() % 24
            val minutes = left.toMinutes() % 60
            return when {
                days > 0 -> "${days}d ${hours}h"
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes.coerceAtLeast(1)}m"
            }
        }
    }
}
