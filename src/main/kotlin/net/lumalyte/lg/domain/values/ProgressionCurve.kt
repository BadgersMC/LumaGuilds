package net.lumalyte.lg.domain.values

import net.lumalyte.lg.config.ProgressionConfig
import kotlin.math.pow

/**
 * The guild leveling curve — single source of truth for XP math.
 *
 * Threshold from [currentLevel] to the next level:
 * `baseXp * (currentLevel + 1)^exponent + (currentLevel + 1) * linearBonusPerLevel`.
 *
 * Used by both the progression service (awards, penalties, menus) and the
 * repository (default rows, stale-value healing) so the two can never disagree.
 * Regression this fixes: `GuildProgression.create()` hardcoded a *different*
 * formula (`800 * (level-1)^1.3 + level*200`), so freshly created guilds showed
 * 1200 XP-to-next while every guild that had earned XP showed 2369 at level 1.
 */
class ProgressionCurve(
    private val baseXp: Double,
    private val exponent: Double,
    private val linearBonusPerLevel: Int,
    private val maxLevel: Int = 100,
) {

    /** XP required to go from [currentLevel] to the next level. */
    fun experienceForNextLevel(currentLevel: Int): Int {
        if (currentLevel >= maxLevel) return 0
        val nextLevel = currentLevel + 1
        return (baseXp * nextLevel.toDouble().pow(exponent) + (nextLevel * linearBonusPerLevel)).toInt()
    }

    /** Cumulative XP required to reach the start of [targetLevel]. */
    fun totalExperienceForLevel(targetLevel: Int): Int {
        if (targetLevel <= 1) return 0
        var totalXp = 0
        for (level in 1 until targetLevel) {
            totalXp += experienceForNextLevel(level)
        }
        return totalXp
    }

    /** Level for a given total XP, capped at permanent level 100. */
    fun levelFromExperience(totalExperience: Int): Int {
        if (totalExperience <= 0) return 1
        if (maxLevel == 1) return 1

        var currentLevel = 1
        var experienceUsed = 0
        while (true) {
            val xpNeeded = experienceForNextLevel(currentLevel)
            if (experienceUsed + xpNeeded > totalExperience) break
            experienceUsed += xpNeeded
            currentLevel++

            if (currentLevel >= maxLevel) break
        }
        return currentLevel
    }

    /** XP earned within the current level (0 .. threshold-1). */
    fun experienceInCurrentLevel(totalExperience: Int): Int {
        val level = levelFromExperience(totalExperience)
        if (level >= maxLevel) return 0
        return totalExperience - totalExperienceForLevel(level)
    }

    companion object {
        /**
         * The single config-to-curve accessor. Every caller (progression service,
         * repository) must go through this so the curve can never be constructed
         * differently in two places.
         */
        fun from(config: ProgressionConfig): ProgressionCurve =
            ProgressionCurve(config.baseXp, config.levelExponent, config.linearBonusPerLevel, config.maxLevel)
    }
}
