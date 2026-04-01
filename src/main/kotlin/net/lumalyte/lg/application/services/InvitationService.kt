package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildInvitation
import java.util.UUID

/**
 * Service for managing guild invitations.
 */
interface InvitationService {
    /**
     * Sends a guild invitation to a player.
     *
     * @param guildId The ID of the guild.
     * @param invitedPlayerId The ID of the player being invited.
     * @param inviterPlayerId The ID of the player sending the invitation.
     * @return true if the invitation was sent successfully, false otherwise.
     */
    fun sendInvitation(guildId: UUID, invitedPlayerId: UUID, inviterPlayerId: UUID): Boolean

    /**
     * Accepts a guild invitation.
     *
     * @param playerId The ID of the player accepting the invitation.
     * @param guildId The ID of the guild.
     * @return true if the invitation was accepted successfully, false otherwise.
     */
    fun acceptInvitation(playerId: UUID, guildId: UUID): Boolean

    /**
     * Declines a guild invitation.
     *
     * @param playerId The ID of the player declining the invitation.
     * @param guildId The ID of the guild.
     * @return true if the invitation was declined successfully, false otherwise.
     */
    fun declineInvitation(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets all pending invitations for a player.
     *
     * @param playerId The ID of the player.
     * @return A list of pending invitations.
     */
    fun getPlayerInvitations(playerId: UUID): List<GuildInvitation>

    /**
     * Cancels a pending invitation.
     *
     * @param playerId The ID of the invited player.
     * @param guildId The ID of the guild.
     * @param cancellerPlayerId The ID of the player cancelling the invitation.
     * @return true if the invitation was cancelled successfully, false otherwise.
     */
    fun cancelInvitation(playerId: UUID, guildId: UUID, cancellerPlayerId: UUID): Boolean

    /**
     * Checks if a player has a pending invitation from a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player has a pending invitation, false otherwise.
     */
    fun hasInvitation(playerId: UUID, guildId: UUID): Boolean
}
