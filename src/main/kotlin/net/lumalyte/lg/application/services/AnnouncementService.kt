package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Announcement
import java.time.Instant
import java.util.UUID

/**
 * Service interface for managing guild announcements.
 */
interface AnnouncementService {
    /**
     * Creates a new guild announcement.
     *
     * @param guildId The ID of the guild.
     * @param authorId The ID of the author.
     * @param title The announcement title.
     * @param message The announcement message.
     * @param isPinned Whether to pin the announcement.
     * @param expiresAt Optional expiration time.
     * @return The created announcement if successful, null otherwise.
     */
    fun createAnnouncement(
        guildId: UUID,
        authorId: UUID,
        title: String,
        message: String,
        isPinned: Boolean = false,
        expiresAt: Instant? = null
    ): Announcement?

    /**
     * Gets all announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @param includeExpired Whether to include expired announcements.
     * @return List of announcements sorted by creation date (newest first).
     */
    fun getAnnouncements(guildId: UUID, includeExpired: Boolean = false): List<Announcement>

    /**
     * Gets pinned announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of pinned announcements.
     */
    fun getPinnedAnnouncements(guildId: UUID): List<Announcement>

    /**
     * Gets a specific announcement by ID.
     *
     * @param announcementId The ID of the announcement.
     * @return The announcement if found, null otherwise.
     */
    fun getAnnouncement(announcementId: UUID): Announcement?

    /**
     * Updates an announcement.
     *
     * @param announcementId The ID of the announcement.
     * @param title The new title.
     * @param message The new message.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun updateAnnouncement(
        announcementId: UUID,
        title: String,
        message: String,
        actorId: UUID
    ): Boolean

    /**
     * Deletes an announcement.
     *
     * @param announcementId The ID of the announcement.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun deleteAnnouncement(announcementId: UUID, actorId: UUID): Boolean

    /**
     * Pins or unpins an announcement.
     *
     * @param announcementId The ID of the announcement.
     * @param isPinned Whether to pin or unpin.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setPinned(announcementId: UUID, isPinned: Boolean, actorId: UUID): Boolean

    /**
     * Checks if a player can create announcements in a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player has permission, false otherwise.
     */
    fun canCreateAnnouncement(playerId: UUID, guildId: UUID): Boolean

    /**
     * Checks if a player can manage (edit/delete) an announcement.
     *
     * @param playerId The ID of the player.
     * @param announcementId The ID of the announcement.
     * @return true if the player has permission, false otherwise.
     */
    fun canManageAnnouncement(playerId: UUID, announcementId: UUID): Boolean

    /**
     * Gets the count of announcements for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The count of active announcements.
     */
    fun getAnnouncementCount(guildId: UUID): Int

    /**
     * Cleans up expired announcements.
     *
     * @return The number of announcements deleted.
     */
    fun cleanupExpired(): Int
}
