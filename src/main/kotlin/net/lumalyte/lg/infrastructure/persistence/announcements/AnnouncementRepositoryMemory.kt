package net.lumalyte.lg.infrastructure.persistence.announcements

import net.lumalyte.lg.application.persistence.AnnouncementRepository
import net.lumalyte.lg.domain.entities.Announcement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of AnnouncementRepository.
 * Suitable for announcements that don't need to persist across server restarts.
 */
class AnnouncementRepositoryMemory : AnnouncementRepository {
    private val announcements = ConcurrentHashMap<UUID, Announcement>()

    override fun getByGuild(guildId: UUID, includeExpired: Boolean): List<Announcement> {
        return announcements.values
            .filter { it.guildId == guildId }
            .filter { includeExpired || it.isActive() }
            .sortedByDescending { it.createdAt }
    }

    override fun getById(id: UUID): Announcement? {
        return announcements[id]
    }

    override fun getPinnedByGuild(guildId: UUID): List<Announcement> {
        return announcements.values
            .filter { it.guildId == guildId && it.isPinned && it.isActive() }
            .sortedByDescending { it.createdAt }
    }

    override fun add(announcement: Announcement): Boolean {
        announcements[announcement.id] = announcement
        return true
    }

    override fun update(announcement: Announcement): Boolean {
        if (!announcements.containsKey(announcement.id)) {
            return false
        }
        announcements[announcement.id] = announcement
        return true
    }

    override fun delete(id: UUID): Boolean {
        return announcements.remove(id) != null
    }

    override fun deleteByGuild(guildId: UUID): Boolean {
        val toDelete = announcements.values.filter { it.guildId == guildId }.map { it.id }
        toDelete.forEach { announcements.remove(it) }
        return true
    }

    override fun setPinned(id: UUID, isPinned: Boolean): Boolean {
        val announcement = announcements[id] ?: return false
        announcements[id] = announcement.copy(isPinned = isPinned)
        return true
    }

    override fun getCountByGuild(guildId: UUID, includeExpired: Boolean): Int {
        return getByGuild(guildId, includeExpired).size
    }

    override fun deleteExpired(): Int {
        val expired = announcements.values.filter { it.isExpired() }.map { it.id }
        expired.forEach { announcements.remove(it) }
        return expired.size
    }
}
