package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.*
import java.time.Instant
import java.util.UUID

/**
 * Service interface for managing leaderboards.
 */
interface LeaderboardService {

    /**
     * Gets a leaderboard by type and period.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @return The leaderboard if available, null otherwise.
     */
    fun getLeaderboard(type: LeaderboardType, period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME): Leaderboard?

    /**
     * Gets paginated entries from a leaderboard.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @param page The page number (0-based).
     * @param pageSize The number of entries per page.
     * @return List of leaderboard entries for the page.
     */
    fun getLeaderboardPage(
        type: LeaderboardType,
        period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
        page: Int = 0,
        pageSize: Int = 10
    ): List<LeaderboardEntry>

    /**
     * Gets the rank for a specific entity on a leaderboard.
     *
     * @param type The type of leaderboard.
     * @param entityId The ID of the entity (guild or player).
     * @param period The time period.
     * @return The rank if found, null otherwise.
     */
    fun getEntityRank(type: LeaderboardType, entityId: UUID, period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME): Int?

    /**
     * Gets the entry for a specific entity on a leaderboard.
     *
     * @param type The type of leaderboard.
     * @param entityId The ID of the entity.
     * @param period The time period.
     * @return The leaderboard entry if found, null otherwise.
     */
    fun getEntityEntry(type: LeaderboardType, entityId: UUID, period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME): LeaderboardEntry?

    /**
     * Updates leaderboard data for a specific entity.
     *
     * @param type The type of leaderboard.
     * @param entityId The ID of the entity.
     * @param newValue The new value for the entity.
     * @param period The time period.
     * @return true if successful, false otherwise.
     */
    fun updateEntityValue(type: LeaderboardType, entityId: UUID, newValue: Double, period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME): Boolean

    /**
     * Refreshes a leaderboard by recalculating all entries.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @return true if successful, false otherwise.
     */
    fun refreshLeaderboard(type: LeaderboardType, period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME): Boolean

    /**
     * Refreshes all leaderboards.
     *
     * @return The number of leaderboards refreshed.
     */
    fun refreshAllLeaderboards(): Int

    /**
     * Gets weekly activity data for a guild.
     *
     * @param guildId The ID of the guild.
     * @param weekStart The start of the week.
     * @return The weekly activity data.
     */
    fun getWeeklyActivity(guildId: UUID, weekStart: Instant): WeeklyActivity?

    /**
     * Updates weekly activity for a guild.
     *
     * @param activity The updated activity data.
     * @return true if successful, false otherwise.
     */
    fun updateWeeklyActivity(activity: WeeklyActivity): Boolean

    /**
     * Records an activity event for a guild.
     *
     * @param guildId The ID of the guild.
     * @param activityType The type of activity.
     * @param value The value to add (default 1).
     * @return true if successful, false otherwise.
     */
    fun recordActivity(guildId: UUID, activityType: ActivityType, value: Int = 1): Boolean

    /**
     * Resets a leaderboard for a new period.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @return true if successful, false otherwise.
     */
    fun resetLeaderboard(type: LeaderboardType, period: LeaderboardPeriod): Boolean

    /**
     * Gets leaderboard snapshots for historical data.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @param limit The maximum number of snapshots to return.
     * @return List of leaderboard snapshots.
     */
    fun getLeaderboardSnapshots(type: LeaderboardType, period: LeaderboardPeriod, limit: Int = 10): List<LeaderboardSnapshot>

    /**
     * Creates a snapshot of the current leaderboard.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @return true if successful, false otherwise.
     */
    fun createLeaderboardSnapshot(type: LeaderboardType, period: LeaderboardPeriod): Boolean

    /**
     * Gets the top entities for a leaderboard.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @param limit The maximum number of entities to return.
     * @return List of top entities with their values.
     */
    fun getTopEntities(type: LeaderboardType, period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME, limit: Int = 10): List<LeaderboardEntry>

    /**
     * Checks if a leaderboard is enabled.
     *
     * @param type The type of leaderboard.
     * @return true if enabled, false otherwise.
     */
    fun isLeaderboardEnabled(type: LeaderboardType): Boolean

    /**
     * Enables or disables a leaderboard.
     *
     * @param type The type of leaderboard.
     * @param enabled Whether to enable or disable.
     * @return true if successful, false otherwise.
     */
    fun setLeaderboardEnabled(type: LeaderboardType, enabled: Boolean): Boolean

    /**
     * Gets leaderboard configuration.
     *
     * @param type The type of leaderboard.
     * @return The configuration if found, null otherwise.
     */
    fun getLeaderboardConfig(type: LeaderboardType): LeaderboardConfig?

    /**
     * Updates leaderboard configuration.
     *
     * @param config The updated configuration.
     * @return true if successful, false otherwise.
     */
    fun updateLeaderboardConfig(config: LeaderboardConfig): Boolean

    /**
     * Processes scheduled leaderboard resets.
     *
     * @return The number of leaderboards reset.
     */
    fun processScheduledResets(): Int

    /**
     * Gets the next reset time for a leaderboard.
     *
     * @param type The type of leaderboard.
     * @param period The time period.
     * @return The next reset time if scheduled, null otherwise.
     */
    fun getNextResetTime(type: LeaderboardType, period: LeaderboardPeriod): Instant?
}

/**
 * Types of guild activities that can be tracked.
 */
enum class ActivityType {
    KILL,
    DEATH,
    CLAIM_CREATED,
    CLAIM_DESTROYED,
    MEMBER_JOINED,
    MEMBER_LEFT,
    BANK_DEPOSIT,
    BANK_WITHDRAWAL,
    CHAT_MESSAGE,
    PARTY_FORMED,
    RELATION_CHANGED,
    WAR_DECLARED,
    WAR_ENDED
}
