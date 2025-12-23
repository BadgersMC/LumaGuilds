package net.lumalyte.lg.application.services

import java.time.Instant
import java.util.UUID

/**
 * Service interface for guild progression and leveling.
 */
interface ProgressionService {

    /**
     * Awards experience points to a guild.
     *
     * @param guildId The ID of the guild.
     * @param experience The amount of experience to award.
     * @param source The source of the experience.
     * @return The new guild level if leveled up, null otherwise.
     */
    fun awardExperience(guildId: UUID, experience: Int, source: ExperienceSource): Int?

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
}

/**
 * Sources of experience for guilds.
 */
enum class ExperienceSource {
    // Guild Activities
    BANK_DEPOSIT,
    MEMBER_JOINED,
    WAR_WON,
    WAR_LOST,
    
    // Player Activities
    PLAYER_KILL,
    MOB_KILL,
    CROP_BREAK,
    BLOCK_BREAK,
    BLOCK_PLACE,
    CRAFTING,
    SMELTING,
    FISHING,
    ENCHANTING,
    
    // Claims (if enabled)
    CLAIM_CREATED,
    CLAIM_DESTROYED,
    
    // System
    WEEKLY_ACTIVITY,
    ADMIN_BONUS
}

/**
 * Types of perks that can be unlocked.
 */
enum class PerkType {
    // Claim perks (if claims enabled)
    INCREASED_CLAIM_BLOCKS,
    INCREASED_CLAIM_COUNT,
    FASTER_CLAIM_REGEN,

    // Bank perks
    HIGHER_BANK_BALANCE,
    BANK_INTEREST,
    INCREASED_BANK_LIMIT,
    REDUCED_WITHDRAWAL_FEES,

    // Home perks
    ADDITIONAL_HOMES,
    TELEPORT_COOLDOWN_REDUCTION,
    HOME_TELEPORT_SOUND_EFFECTS,

    // Audio/Visual perks (always unlocked)
    CUSTOM_BANNER_COLORS,
    ANIMATED_EMOJIS,
    SPECIAL_PARTICLES,
    ANNOUNCEMENT_SOUND_EFFECTS,
    WAR_DECLARATION_SOUND_EFFECTS,

    // System perks
    // (No system perks currently defined)
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
