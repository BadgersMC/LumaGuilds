package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DiplomaticHistory
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for managing diplomatic history persistence.
 */
interface DiplomaticHistoryRepository {

    /**
     * Adds a new diplomatic history event to the repository.
     *
     * @param history The history event to add.
     * @return true if the event was added successfully, false otherwise.
     */
    fun add(history: DiplomaticHistory): Boolean

    /**
     * Gets a diplomatic history event by its ID.
     *
     * @param historyId The ID of the history event to retrieve.
     * @return The history event if found, null otherwise.
     */
    fun getById(historyId: UUID): DiplomaticHistory?

    /**
     * Gets all diplomatic history events for a specific guild.
     *
     * @param guildId The ID of the guild to get history for.
     * @return A list of history events involving the guild.
     */
    fun getByGuild(guildId: UUID): List<DiplomaticHistory>

    /**
     * Gets diplomatic history events for a guild filtered by event type.
     *
     * @param guildId The ID of the guild.
     * @param eventType The type of event to filter by.
     * @return A list of history events of the specified type.
     */
    fun getByGuildAndType(guildId: UUID, eventType: String): List<DiplomaticHistory>

    /**
     * Gets diplomatic history events within a time range.
     *
     * @param guildId The ID of the guild.
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A list of history events within the time range.
     */
    fun getByGuildAndTimeRange(guildId: UUID, startTime: Instant, endTime: Instant): List<DiplomaticHistory>

    /**
     * Gets diplomatic history events between two guilds.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @return A list of history events between the guilds.
     */
    fun getBetweenGuilds(guildA: UUID, guildB: UUID): List<DiplomaticHistory>

    /**
     * Gets all diplomatic history events.
     *
     * @return A list of all history events.
     */
    fun getAll(): List<DiplomaticHistory>

    /**
     * Gets the most recent diplomatic history events for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit The maximum number of events to return.
     * @return A list of the most recent history events.
     */
    fun getRecentByGuild(guildId: UUID, limit: Int = 50): List<DiplomaticHistory>

    /**
     * Gets diplomatic history events by event type.
     *
     * @param eventType The type of event to filter by.
     * @return A list of history events of the specified type.
     */
    fun getByType(eventType: String): List<DiplomaticHistory>

    /**
     * Removes old diplomatic history events beyond a certain age.
     *
     * @param olderThan Remove events older than this timestamp.
     * @return The number of events removed.
     */
    fun cleanupOldEvents(olderThan: Instant): Int
}
