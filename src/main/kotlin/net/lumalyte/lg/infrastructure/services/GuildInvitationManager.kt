package net.lumalyte.lg.infrastructure.services

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages pending guild invitations in memory.
 * For beta testing - in production this should be persisted to database.
 */
object GuildInvitationManager {

    private data class PendingInvite(
        val guildId: UUID,
        val guildName: String,
        val invitedPlayerId: UUID,
        val inviterPlayerId: UUID,
        val inviterName: String,
        val timestamp: Instant = Instant.now()
    )

    // Map of player UUID -> list of pending invites
    private val pendingInvites = ConcurrentHashMap<UUID, MutableList<PendingInvite>>()

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
        val invite = PendingInvite(
            guildId = guildId,
            guildName = guildName,
            invitedPlayerId = invitedPlayerId,
            inviterPlayerId = inviterPlayerId,
            inviterName = inviterName
        )

        pendingInvites.computeIfAbsent(invitedPlayerId) { mutableListOf() }.add(invite)
    }

    /**
     * Get all pending invites for a player
     */
    fun getInvites(playerId: UUID): List<Pair<UUID, String>> {
        return pendingInvites[playerId]?.map { it.guildId to it.guildName } ?: emptyList()
    }

    /**
     * Get a specific invite by guild name for a player
     */
    fun getInviteByGuildName(playerId: UUID, guildName: String): Pair<UUID, String>? {
        return pendingInvites[playerId]?.firstOrNull {
            it.guildName.equals(guildName, ignoreCase = true)
        }?.let { it.guildId to it.guildName }
    }

    /**
     * Remove a specific invite
     */
    fun removeInvite(playerId: UUID, guildId: UUID) {
        pendingInvites[playerId]?.removeIf { it.guildId == guildId }
        if (pendingInvites[playerId]?.isEmpty() == true) {
            pendingInvites.remove(playerId)
        }
    }

    /**
     * Remove all invites for a player
     */
    fun clearInvites(playerId: UUID) {
        pendingInvites.remove(playerId)
    }

    /**
     * Check if player has a pending invite from a specific guild
     */
    fun hasInvite(playerId: UUID, guildId: UUID): Boolean {
        return pendingInvites[playerId]?.any { it.guildId == guildId } ?: false
    }

    /**
     * Get the number of pending invites for a player
     */
    fun getInviteCount(playerId: UUID): Int {
        return pendingInvites[playerId]?.size ?: 0
    }

    /**
     * Clean up old invites (older than 24 hours)
     */
    fun cleanupOldInvites() {
        val oneDayAgo = Instant.now().minusSeconds(86400)
        pendingInvites.values.forEach { inviteList ->
            inviteList.removeIf { it.timestamp.isBefore(oneDayAgo) }
        }
        pendingInvites.entries.removeIf { it.value.isEmpty() }
    }
}
