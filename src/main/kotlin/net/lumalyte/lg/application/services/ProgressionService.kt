package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PerkType
import java.time.Instant
import java.util.UUID

/**
 * Service interface for guild progression and leveling.
 */
interface ProgressionService {

    /**
     * Awards experience to a guild.
     *
     * @param guildId The ID of the guild.
     * @param experience The amount of experience to award.
     * @param source The source of the experience.
     * @return The new guild level if leveled up, null otherwise.
     */
    fun awardExperience(guildId: UUID, experience: Int, source: ExperienceSource): Int?

    /** Awards configured units of player activity while retaining the actor for AFK validation. */
    fun awardPlayerActivity(
        guildId: UUID,
        actorId: UUID,
        units: Int,
        source: ExperienceSource,
        eligible: Boolean = true,
    ): Int? = awardExperience(guildId, units, source)

    /** Awards trusted system XP that is contractually outside source caps. */
    fun awardUncappedSystemExperience(guildId: UUID, experience: Int, source: ExperienceSource): Int? =
        awardExperience(guildId, experience, source)

    /**
     * Removes experience from a guild (used by Guild Strikes EXP penalties).
     * Never drops total XP below 0. Recalculates level and syncs the guild's
     * level field.
     *
     * @return The new guild level.
     */
    fun removeExperience(guildId: UUID, amount: Int, source: ExperienceSource): Int

    /**
     * Reduces a guild's level by the given number of levels (used by Guild
     * Strikes Level Reduction penalties). Total XP is set to the threshold of
     * the target level; level never drops below 1.
     *
     * @return The new guild level.
     */
    fun reduceLevel(guildId: UUID, levels: Int, source: ExperienceSource): Int

    /**
     * Calculates the experience required for the next level.
     *
     * @param currentLevel The current level of the guild.
     * @return The experience required for the next level.
     */
    fun getExperienceForNextLevel(currentLevel: Int): Int

    /**
     * Calculates the total experience required to reach a specific level.
     *
     * @param targetLevel The target level.
     * @return The total experience required.
     */
    fun getTotalExperienceForLevel(targetLevel: Int): Int

    /**
     * Gets the current level of a guild based on its experience.
     *
     * @param totalExperience The total experience of the guild.
     * @return The current level.
     */
    fun getLevelFromExperience(totalExperience: Int): Int

    /**
     * Gets the experience progress within the current level.
     *
     * @param totalExperience The total experience of the guild.
     * @return A pair of (current level experience, experience needed for next level).
     */
    fun getLevelProgress(totalExperience: Int): Pair<Int, Int>

    /**
     * Gets the perks unlocked at a specific level.
     *
     * @param level The level to check.
     * @return List of perk types unlocked at this level.
     */
    fun getPerksForLevel(level: Int): List<PerkType>

    /**
     * Checks if a guild has a specific perk unlocked.
     *
     * @param guildId The ID of the guild.
     * @param perkType The type of perk to check.
     * @return true if the perk is unlocked, false otherwise.
     */
    fun hasPerkUnlocked(guildId: UUID, perkType: PerkType): Boolean

    /**
     * Gets all perks unlocked for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of unlocked perk types.
     */
    fun getUnlockedPerks(guildId: UUID): List<PerkType>

    /**
     * Gets the maximum claim blocks allowed for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum claim blocks.
     */
    fun getMaxClaimBlocks(guildId: UUID): Int

    /**
     * Gets the maximum claim count allowed for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum claim count.
     */
    fun getMaxClaimCount(guildId: UUID): Int

    /**
     * Gets the bank interest rate for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The interest rate as a percentage.
     */
    fun getBankInterestRate(guildId: UUID): Double

    /**
     * Calculates weekly activity score for a guild.
     *
     * @param guildId The ID of the guild.
     * @param weekStart The start of the week.
     * @param weekEnd The end of the week.
     * @return The calculated activity score.
     */
    fun calculateWeeklyActivityScore(guildId: UUID, weekStart: Instant, weekEnd: Instant): Int

    /**
     * Gets the top guilds by activity for a given period.
     *
     * @param period The time period.
     * @param limit The maximum number of guilds to return.
     * @return List of guilds with their activity scores.
     */
    fun getTopActiveGuilds(period: ActivityPeriod, limit: Int = 10): List<Pair<UUID, Int>>

    /**
     * Gets the activity percentile for a guild.
     *
     * @param guildId The ID of the guild.
     * @param period The time period.
     * @return The percentile (0.0 to 100.0).
     */
    fun getActivityPercentile(guildId: UUID, period: ActivityPeriod): Double

    /**
     * Resets weekly activity data.
     *
     * @return The number of guilds that had their activity reset.
     */
    fun resetWeeklyActivity(): Int

    /**
     * Gets the maximum number of homes a guild can set based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum number of homes.
     */
    fun getMaxHomes(guildId: UUID): Int

    /**
     * Gets the maximum bank balance allowed for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum bank balance.
     */
    fun getMaxBankBalance(guildId: UUID): Int

    /**
     * Gets the maximum member count for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum number of members.
     */
    fun getMaxMembers(guildId: UUID): Int

    /**
     * Gets the withdrawal fee multiplier for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The withdrawal fee multiplier (1.0 = normal, 0.5 = 50% fees).
     */
    fun getWithdrawalFeeMultiplier(guildId: UUID): Double

    /**
     * Gets the home teleport cooldown multiplier for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The cooldown multiplier (1.0 = normal, 0.6 = 60% of normal).
     */
    fun getHomeCooldownMultiplier(guildId: UUID): Double

    /**
     * Gets the maximum simultaneous wars for a guild based on its level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum number of simultaneous wars.
     */
    fun getMaxWars(guildId: UUID): Int

    /**
     * Processes level up events and applies unlocked perks.
     *
     * @param guildId The ID of the guild.
     * @param newLevel The new level achieved.
     * @return List of newly unlocked perks.
     */
    fun processLevelUp(guildId: UUID, newLevel: Int): List<PerkType>

    /**
     * Recomputes Guild.level from each guild's stored XP and writes back any drift.
     * Cheap repair pass for the bug where Guild.level was never updated alongside
     * GuildProgression.currentLevel. Returns the number of guild rows updated.
     */
    fun syncGuildLevels(): Int

    /** Returns one authoritative allowance view per configured source pool. */
    fun getSourceUsage(guildId: UUID, at: Instant = Instant.now()): List<SourceUsageView>

    /**
     * Returns the amount of XP earned per source for a guild today.
     * Used by the progression menu to display daily caps and progress.
     */
    @Deprecated("Use getSourceUsage")
    fun getDailySourceXp(guildId: UUID): Map<ExperienceSource, Int>

    /**
     * Gets the daily XP cap for a given experience source.
     */
    @Deprecated("Use getSourceUsage")
    fun getDailyCap(source: ExperienceSource): Int
}

/**
 * Time periods for activity tracking.
 */
enum class ActivityPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ALL_TIME
}
