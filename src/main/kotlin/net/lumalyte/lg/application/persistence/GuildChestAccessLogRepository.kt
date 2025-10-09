package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildChestAccessLog
import net.lumalyte.lg.domain.entities.GuildChestAction
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for managing guild chest access logs.
 */
interface GuildChestAccessLogRepository {

    /**
     * Adds a new access log entry.
     *
     * @param log The access log to add.
     * @return true if the log was added successfully, false otherwise.
     */
    fun add(log: GuildChestAccessLog): Boolean

    /**
     * Gets an access log by its ID.
     *
     * @param logId The ID of the access log to retrieve.
     * @return The access log if found, null otherwise.
     */
    fun getById(logId: UUID): GuildChestAccessLog?

    /**
     * Gets all access logs for a specific chest.
     *
     * @param chestId The ID of the chest.
     * @return A list of access logs for the chest.
     */
    fun getByChest(chestId: UUID): List<GuildChestAccessLog>

    /**
     * Gets access logs for a specific player.
     *
     * @param playerId The ID of the player.
     * @return A list of access logs for the player.
     */
    fun getByPlayer(playerId: UUID): List<GuildChestAccessLog>

    /**
     * Gets access logs for a specific action type.
     *
     * @param action The action type to filter by.
     * @return A list of access logs for the action type.
     */
    fun getByAction(action: GuildChestAction): List<GuildChestAccessLog>

    /**
     * Gets access logs within a time range.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A list of access logs within the time range.
     */
    fun getByTimeRange(startTime: Instant, endTime: Instant): List<GuildChestAccessLog>

    /**
     * Gets the most recent access logs for a chest.
     *
     * @param chestId The ID of the chest.
     * @param limit The maximum number of logs to return.
     * @return A list of the most recent access logs.
     */
    fun getRecentByChest(chestId: UUID, limit: Int = 50): List<GuildChestAccessLog>

    /**
     * Gets access logs for suspicious activities (break attempts, unauthorized access).
     *
     * @return A list of suspicious access logs.
     */
    fun getSuspiciousActivities(): List<GuildChestAccessLog>

    /**
     * Cleans up old access logs beyond a certain age.
     *
     * @param olderThan Remove logs older than this timestamp.
     * @return The number of logs removed.
     */
    fun cleanupOldLogs(olderThan: Instant): Int

    /**
     * Gets access logs for a specific guild's chests.
     *
     * @param guildId The ID of the guild.
     * @return A list of access logs for the guild's chests.
     */
    fun getByGuild(guildId: UUID): List<GuildChestAccessLog>
}
