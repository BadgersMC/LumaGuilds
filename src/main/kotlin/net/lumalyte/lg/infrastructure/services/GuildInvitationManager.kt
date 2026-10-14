package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.domain.entities.GuildInvitation
import java.time.Instant
import java.util.UUID

/**
 * Manages pending guild invitations with database persistence.
 * This is a static wrapper around the repository for backwards compatibility.
 */
object GuildInvitationManager {

    private lateinit var repository: GuildInvitationRepository

    private var inviteBlocks: InviteBlockService? = null

    /**
     * Initialize the manager with a repository instance.
     * This must be called during plugin startup.
     */
    fun initialize(
        repo: GuildInvitationRepository,
        blocks: InviteBlockService? = null,
    ) {
        repository = repo
        inviteBlocks = blocks
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
    ): Boolean {
        // Every guild invite passes here: refused, without saying why, when the invited player blocked the inviter or
        // a member of the guild (and when the blocks could not be loaded).
        if (inviteBlocks?.refusesGuildInvite(invitedPlayerId, guildId, inviterPlayerId) == true) return false

        val invitation = GuildInvitation(
            guildId = guildId,
            guildName = guildName,
            invitedPlayerId = invitedPlayerId,
            inviterPlayerId = inviterPlayerId,
            inviterName = inviterName
        )

        if (!repository.add(invitation)) return false

        try {
            val notificationService = org.koin.core.context.GlobalContext.get()
                .getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>()
            notificationService?.notifyGuildInvite(invitedPlayerId, guildName, inviterName)
        } catch (_: Exception) {
            // Apollo is optional; durable invitation persistence already succeeded.
        }
        return true
    }

    /**
     * Get all pending invites for a player
     */
    fun getInvites(playerId: UUID): List<Pair<UUID, String>> {
        // An invite stops counting once the player blocks the inviter or someone who is now in that guild.
        return repository.getByPlayer(playerId)
            .filterNot { inviteBlocks?.refusesGuildInvite(playerId, it.guildId, it.inviterPlayerId) == true }
            .map { it.guildId to it.guildName }
    }

    /**
     * Get a specific invite by guild name for a player
     */
    fun getInviteByGuildName(playerId: UUID, guildName: String): Pair<UUID, String>? {
        return repository.getByPlayer(playerId)
            .firstOrNull { it.guildName.equals(guildName, ignoreCase = true) }
            ?.takeUnless { inviteBlocks?.refusesGuildInvite(playerId, it.guildId, it.inviterPlayerId) == true }
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
        if (inviteBlocks == null) return repository.hasInvitation(playerId, guildId)
        return getInvites(playerId).any { it.first == guildId }
    }

    /**
     * Get the number of pending invites for a player
     */
    fun getInviteCount(playerId: UUID): Int {
        if (inviteBlocks == null) return repository.getInvitationCount(playerId)
        return getInvites(playerId).size
    }

    /**
     * Clean up old invites (older than 24 hours)
     */
    fun cleanupOldInvites() {
        val oneDayAgo = Instant.now().minusSeconds(86400)
        repository.removeOlderThan(oneDayAgo.epochSecond)
    }
}
