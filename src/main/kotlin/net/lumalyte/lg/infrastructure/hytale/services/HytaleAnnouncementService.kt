package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.AnnouncementRepository
import net.lumalyte.lg.application.services.AnnouncementService
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Announcement
import net.lumalyte.lg.domain.entities.RankPermission
import java.time.Instant
import java.util.UUID

/**
 * Hytale implementation of AnnouncementService.
 *
 * Handles guild announcement operations including creation, management, and notifications.
 */
class HytaleAnnouncementService(
    private val announcementRepository: AnnouncementRepository,
    private val rankService: RankService,
    private val playerService: PlayerService
) : AnnouncementService {

    override fun createAnnouncement(
        guildId: UUID,
        authorId: UUID,
        title: String,
        message: String,
        isPinned: Boolean,
        expiresAt: Instant?
    ): Announcement? {
        // Check permission
        if (!canCreateAnnouncement(authorId, guildId)) {
            return null
        }

        // Get author name
        val authorName = playerService.getPlayerName(authorId) ?: "Unknown"

        // Create announcement
        val announcement = Announcement(
            guildId = guildId,
            authorId = authorId,
            authorName = authorName,
            title = title,
            message = message,
            isPinned = isPinned,
            expiresAt = expiresAt
        )

        return if (announcementRepository.add(announcement)) {
            announcement
        } else {
            null
        }
    }

    override fun getAnnouncements(guildId: UUID, includeExpired: Boolean): List<Announcement> {
        return announcementRepository.getByGuild(guildId, includeExpired)
    }

    override fun getPinnedAnnouncements(guildId: UUID): List<Announcement> {
        return announcementRepository.getPinnedByGuild(guildId)
    }

    override fun getAnnouncement(announcementId: UUID): Announcement? {
        return announcementRepository.getById(announcementId)
    }

    override fun updateAnnouncement(announcementId: UUID, title: String, message: String, actorId: UUID): Boolean {
        val announcement = announcementRepository.getById(announcementId) ?: return false

        // Check permission
        if (!canManageAnnouncement(actorId, announcementId)) {
            return false
        }

        // Update announcement
        val updated = announcement.copy(title = title, message = message)
        return announcementRepository.update(updated)
    }

    override fun deleteAnnouncement(announcementId: UUID, actorId: UUID): Boolean {
        val announcement = announcementRepository.getById(announcementId) ?: return false

        // Check permission
        if (!canManageAnnouncement(actorId, announcementId)) {
            return false
        }

        return announcementRepository.delete(announcementId)
    }

    override fun setPinned(announcementId: UUID, isPinned: Boolean, actorId: UUID): Boolean {
        val announcement = announcementRepository.getById(announcementId) ?: return false

        // Check permission
        if (!canManageAnnouncement(actorId, announcementId)) {
            return false
        }

        return announcementRepository.setPinned(announcementId, isPinned)
    }

    override fun canCreateAnnouncement(playerId: UUID, guildId: UUID): Boolean {
        return rankService.hasPermission(playerId, guildId, RankPermission.SEND_ANNOUNCEMENTS)
    }

    override fun canManageAnnouncement(playerId: UUID, announcementId: UUID): Boolean {
        val announcement = announcementRepository.getById(announcementId) ?: return false

        // Author can always manage their own announcements
        if (announcement.authorId == playerId) {
            return true
        }

        // Check if player has announcement permission in the guild
        return rankService.hasPermission(playerId, announcement.guildId, RankPermission.SEND_ANNOUNCEMENTS)
    }

    override fun getAnnouncementCount(guildId: UUID): Int {
        return announcementRepository.getCountByGuild(guildId, includeExpired = false)
    }

    override fun cleanupExpired(): Int {
        return announcementRepository.deleteExpired()
    }
}
