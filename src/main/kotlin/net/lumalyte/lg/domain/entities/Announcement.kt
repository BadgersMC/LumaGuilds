package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a guild announcement.
 *
 * @property id The unique identifier for the announcement.
 * @property guildId The ID of the guild this announcement belongs to.
 * @property authorId The ID of the player who created the announcement.
 * @property authorName The name of the author (cached for display).
 * @property title The title of the announcement.
 * @property message The announcement message content.
 * @property isPinned Whether this announcement is pinned to the top.
 * @property createdAt When the announcement was created.
 * @property expiresAt Optional expiration time for the announcement.
 */
data class Announcement(
    val id: UUID = UUID.randomUUID(),
    val guildId: UUID,
    val authorId: UUID,
    val authorName: String,
    val title: String,
    val message: String,
    val isPinned: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
) {
    init {
        require(title.isNotBlank()) { "Announcement title cannot be blank" }
        require(title.length <= 100) { "Announcement title must be 100 characters or less" }
        require(message.isNotBlank()) { "Announcement message cannot be blank" }
        require(message.length <= 500) { "Announcement message must be 500 characters or less" }

        expiresAt?.let { expiry ->
            require(expiry.isAfter(createdAt)) { "Expiration time must be after creation time" }
        }
    }

    /**
     * Checks if this announcement has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { it.isBefore(Instant.now()) } ?: false
    }

    /**
     * Checks if this announcement is currently active (not expired).
     */
    fun isActive(): Boolean {
        return !isExpired()
    }
}
