package net.lumalyte.lg.domain.values

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
) {

    /** XP required to go from [currentLevel] to the next level. */
    fun experienceForNextLevel(currentLevel: Int): Int {
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

    /**
     * Level for a given total XP. Capped at 101 — the level-up loop's safety cap
     * (matches the historical behaviour: level 101 is the max, XP keeps
     * accumulating beyond it as a sink).
     */
    fun levelFromExperience(totalExperience: Int): Int {
        if (totalExperience <= 0) return 1

        var currentLevel = 1
        var experienceUsed = 0
        while (true) {
            val xpNeeded = experienceForNextLevel(currentLevel)
            if (experienceUsed + xpNeeded > totalExperience) break
            experienceUsed += xpNeeded
            currentLevel++

            // Safety check to prevent infinite loops
            if (currentLevel > 100) break
        }
        return currentLevel
    }

    /** XP earned within the current level (0 .. threshold-1). */
    fun experienceInCurrentLevel(totalExperience: Int): Int =
        totalExperience - totalExperienceForLevel(levelFromExperience(totalExperience))
}
