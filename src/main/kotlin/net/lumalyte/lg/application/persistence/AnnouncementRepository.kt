package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Announcement
import java.util.UUID

/**
 * Repository for managing guild announcement persistence.
 */
interface AnnouncementRepository {
    /**
     * Gets all announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @param includeExpired Whether to include expired announcements.
     * @return List of announcements.
     */
    fun getByGuild(guildId: UUID, includeExpired: Boolean = false): List<Announcement>

    /**
     * Gets a specific announcement by ID.
     *
     * @param id The ID of the announcement.
     * @return The announcement if found, null otherwise.
     */
    fun getById(id: UUID): Announcement?

    /**
     * Gets all pinned announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of pinned announcements.
     */
    fun getPinnedByGuild(guildId: UUID): List<Announcement>

    /**
     * Creates a new announcement.
     *
     * @param announcement The announcement to create.
     * @return true if successful, false otherwise.
     */
    fun add(announcement: Announcement): Boolean

    /**
     * Updates an existing announcement.
     *
     * @param announcement The announcement to update.
     * @return true if successful, false otherwise.
     */
    fun update(announcement: Announcement): Boolean

    /**
     * Deletes an announcement.
     *
     * @param id The ID of the announcement to delete.
     * @return true if successful, false otherwise.
     */
    fun delete(id: UUID): Boolean

    /**
     * Deletes all announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun deleteByGuild(guildId: UUID): Boolean

    /**
     * Pins or unpins an announcement.
     *
     * @param id The ID of the announcement.
     * @param isPinned Whether to pin or unpin.
     * @return true if successful, false otherwise.
     */
    fun setPinned(id: UUID, isPinned: Boolean): Boolean

    /**
     * Gets the count of announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @param includeExpired Whether to include expired announcements.
     * @return The count of announcements.
     */
    fun getCountByGuild(guildId: UUID, includeExpired: Boolean = false): Int

    /**
     * Deletes expired announcements.
     *
     * @return The number of announcements deleted.
     */
    fun deleteExpired(): Int
}
