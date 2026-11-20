package net.lumalyte.lg.domain.values

import kotlin.math.floor

/**
 * REQ-179: Quick Travel's multiplier (1.0 = none, 0.5 = Quick Travel 5) applies to
 * the home teleport countdown and the cooldown between home teleports.
 */
object HomeTravelTimes {
    const val BASE_COUNTDOWN_SECONDS = 5

    fun countdownSeconds(multiplier: Double): Int {
        val m = if (multiplier.isNaN()) 1.0 else multiplier.coerceIn(0.0, 1.0)
        return floor(BASE_COUNTDOWN_SECONDS * m + EPSILON).toInt().coerceIn(1, BASE_COUNTDOWN_SECONDS)
    }

    fun cooldownSeconds(baseSeconds: Int, multiplier: Double): Long {
        val m = if (multiplier.isNaN()) 1.0 else multiplier.coerceIn(0.0, 1.0)
        return (baseSeconds.coerceAtLeast(0) * m).toLong()
    }

    private const val EPSILON = 1e-9
}
