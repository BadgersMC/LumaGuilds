package net.lumalyte.lg.domain.rewards

import net.lumalyte.lg.config.PrestigeCarryOverConfig
import net.lumalyte.lg.domain.values.ProgressionCurve

/**
 * REQ-175: XP a guild earned after reaching level 100 can start its next climb.
 * The carry is capped at the XP needed to reach the start of the landing level,
 * so a guild that sat at 100 for months does not skip most of the next climb.
 */
object PrestigeCarryOver {
    private const val CAP_LEVEL = 100

    fun experience(totalExperience: Int, curve: ProgressionCurve, settings: PrestigeCarryOverConfig): Int {
        if (!settings.enabled) return 0
        val excess = (totalExperience - curve.totalExperienceForLevel(CAP_LEVEL)).coerceAtLeast(0)
        return minOf(excess, curve.totalExperienceForLevel(settings.landingLevel))
    }
}
