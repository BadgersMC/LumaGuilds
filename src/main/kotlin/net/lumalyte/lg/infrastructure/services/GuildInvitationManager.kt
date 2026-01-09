package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.domain.entities.GuildInvitation
import java.time.Instant
import java.util.UUID

/**
 * Manages pending guild invitations with database persistence.
 * This is a static wrapper around the repository for backwards compatibility.
 */
object GuildInvitationManager {

    private lateinit var repository: GuildInvitationRepository

    /**
     * Initialize the manager with a repository instance.
     * This must be called during plugin startup.
     */
    fun initialize(repo: GuildInvitationRepository) {
        repository = repo
    }

    /**
     * Add a pending invitation for a player
     */
    fun addInvite(
        guildId: UUID,
        guildName: String,
        invitedPlayerId: UUID,
        inviterPlayerId: UUID,
        inviterName: String
    ) {
        val invitation = GuildInvitation(
            guildId = guildId,
            guildName = guildName,
            invitedPlayerId = invitedPlayerId,
            inviterPlayerId = inviterPlayerId,
            inviterName = inviterName
        )

        repository.add(invitation)

        // Send Apollo notification (if available)
        try {
            val notificationService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>()
            notificationService?.notifyGuildInvite(invitedPlayerId, guildName, inviterName)
        } catch (e: Exception) {
            // Silently fail if Apollo not available
        }
    }

    /**
     * Get all pending invites for a player
     */
    fun getInvites(playerId: UUID): List<Pair<UUID, String>> {
        return repository.getByPlayer(playerId).map { it.guildId to it.guildName }
    }

    /**
     * Get a specific invite by guild name for a player
     */
    fun getInviteByGuildName(playerId: UUID, guildName: String): Pair<UUID, String>? {
        return repository.getByPlayer(playerId)
            .firstOrNull { it.guildName.equals(guildName, ignoreCase = true) }
            ?.let { it.guildId to it.guildName }
    }

    /**
     * Remove a specific invite
     */
    fun removeInvite(playerId: UUID, guildId: UUID) {
        repository.remove(playerId, guildId)
    }

    /**
     * Remove all invites for a player
     */
    fun clearInvites(playerId: UUID) {
        repository.removeAllForPlayer(playerId)
    }

    /**
     * Check if player has a pending invite from a specific guild
     */
    fun hasInvite(playerId: UUID, guildId: UUID): Boolean {
        return repository.hasInvitation(playerId, guildId)
    }

    /**
     * Get the number of pending invites for a player
     */
    fun getInviteCount(playerId: UUID): Int {
        return repository.getInvitationCount(playerId)
    }

    /**
     * Clean up old invites (older than 24 hours)
     */
    fun cleanupOldInvites() {
        val oneDayAgo = Instant.now().minusSeconds(86400)
        repository.removeOlderThan(oneDayAgo.epochSecond)
    }
}
