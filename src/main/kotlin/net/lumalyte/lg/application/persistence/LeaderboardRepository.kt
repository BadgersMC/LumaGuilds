package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.*
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for leaderboard data persistence.
 */
interface LeaderboardRepository {

    /**
     * Records or updates a leaderboard entry.
     *
     * @param entry The leaderboard entry to save.
     * @return true if successful, false otherwise.
     */
    fun saveLeaderboardEntry(entry: LeaderboardEntry): Boolean

    /**
     * Gets leaderboard entries for a specific type and period.
     *
     * @param type The leaderboard type.
     * @param period The time period.
     * @param limit The maximum number of entries to return.
     * @return List of leaderboard entries.
     */
    fun getLeaderboardEntries(type: ExtendedLeaderboardType, period: LeaderboardPeriod, limit: Int = 100): List<LeaderboardEntry>

    /**
     * Gets a specific leaderboard entry.
     *
     * @param type The leaderboard type.
     * @param entityId The entity ID.
     * @param period The time period.
     * @return The leaderboard entry if found, null otherwise.
     */
    fun getLeaderboardEntry(type: ExtendedLeaderboardType, entityId: UUID, period: LeaderboardPeriod): LeaderboardEntry?

    /**
     * Gets paginated leaderboard entries.
     *
     * @param type The leaderboard type.
     * @param period The time period.
     * @param offset The offset for pagination.
     * @param limit The maximum number of entries to return.
     * @return List of leaderboard entries.
     */
    fun getLeaderboardEntriesPaged(type: ExtendedLeaderboardType, period: LeaderboardPeriod, offset: Int, limit: Int): List<LeaderboardEntry>

    /**
     * Gets the rank of a specific entity.
     *
     * @param type The leaderboard type.
     * @param entityId The entity ID.
     * @param period The time period.
     * @return The rank if found, null otherwise.
     */
    fun getEntityRank(type: ExtendedLeaderboardType, entityId: UUID, period: LeaderboardPeriod): Int?

    /**
     * Updates multiple leaderboard entries in batch.
     *
     * @param entries The entries to update.
     * @return The number of entries successfully updated.
     */
    fun batchUpdateEntries(entries: List<LeaderboardEntry>): Int

    /**
     * Saves a leaderboard snapshot.
     *
     * @param snapshot The snapshot to save.
     * @return true if successful, false otherwise.
     */
    fun saveLeaderboardSnapshot(snapshot: LeaderboardSnapshot): Boolean

    /**
     * Gets leaderboard snapshots for a specific type and period.
     *
     * @param type The leaderboard type.
     * @param period The time period.
     * @param limit The maximum number of snapshots to return.
     * @return List of leaderboard snapshots.
     */
    fun getLeaderboardSnapshots(type: ExtendedLeaderboardType, period: LeaderboardPeriod, limit: Int = 10): List<LeaderboardSnapshot>

    /**
     * Saves weekly activity data.
     *
     * @param activity The weekly activity to save.
     * @return true if successful, false otherwise.
     */
    fun saveWeeklyActivity(activity: WeeklyActivity): Boolean

    /**
     * Gets weekly activity for a guild and period.
     *
     * @param guildId The guild ID.
     * @param weekStart The start of the week.
     * @return The weekly activity if found, null otherwise.
     */
    fun getWeeklyActivity(guildId: UUID, weekStart: Instant): WeeklyActivity?

    /**
     * Gets weekly activity for all guilds in a period.
     *
     * @param weekStart The start of the week.
     * @param limit The maximum number of results.
     * @return List of weekly activities sorted by score.
     */
    fun getWeeklyActivityForPeriod(weekStart: Instant, limit: Int = 100): List<WeeklyActivity>

    /**
     * Saves leaderboard configuration.
     *
     * @param config The configuration to save.
     * @return true if successful, false otherwise.
     */
    fun saveLeaderboardConfig(config: LeaderboardConfig): Boolean

    /**
     * Gets leaderboard configuration.
     *
     * @param type The leaderboard type.
     * @return The configuration if found, null otherwise.
     */
    fun getLeaderboardConfig(type: ExtendedLeaderboardType): LeaderboardConfig?

    /**
     * Deletes old leaderboard entries beyond a certain age.
     *
     * @param maxAgeDays The maximum age in days.
     * @return The number of entries deleted.
     */
    fun deleteOldEntries(maxAgeDays: Int = 90): Int

    /**
     * Resets a leaderboard for a new period.
     *
     * @param type The leaderboard type.
     * @param period The time period.
     * @param newPeriodStart The start of the new period.
     * @return true if successful, false otherwise.
     */
    fun resetLeaderboardPeriod(type: ExtendedLeaderboardType, period: LeaderboardPeriod, newPeriodStart: Instant): Boolean

    /**
     * Gets the total count of entries for a leaderboard.
     *
     * @param type The leaderboard type.
     * @param period The time period.
     * @return The total count of entries.
     */
    fun getLeaderboardEntryCount(type: ExtendedLeaderboardType, period: LeaderboardPeriod): Int

    /**
     * Updates the last updated timestamp for leaderboard entries.
     *
     * @param type The leaderboard type.
     * @param period The time period.
     * @param entityIds The entity IDs to update.
     * @return The number of entries updated.
     */
    fun updateLastUpdated(type: ExtendedLeaderboardType, period: LeaderboardPeriod, entityIds: List<UUID>): Int
}
