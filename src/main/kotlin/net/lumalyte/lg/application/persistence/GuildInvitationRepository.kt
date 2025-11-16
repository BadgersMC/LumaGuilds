package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildInvitation
import java.util.UUID

/**
 * Repository for managing guild invitation persistence.
 */
interface GuildInvitationRepository {
    /**
     * Gets all pending invitations for a player.
     *
     * @param playerId The ID of the invited player.
     * @return A list of pending invitations for the player.
     */
    fun getByPlayer(playerId: UUID): List<GuildInvitation>

    /**
     * Gets a specific invitation by player ID and guild ID.
     *
     * @param playerId The ID of the invited player.
     * @param guildId The ID of the guild.
     * @return The invitation if found, null otherwise.
     */
    fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): GuildInvitation?

    /**
     * Adds a new guild invitation.
     *
     * @param invitation The invitation to add.
     * @return true if successful, false otherwise.
     */
    fun add(invitation: GuildInvitation): Boolean

    /**
     * Removes a specific invitation.
     *
     * @param playerId The ID of the invited player.
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun remove(playerId: UUID, guildId: UUID): Boolean

    /**
     * Removes all invitations for a player.
     *
     * @param playerId The ID of the player.
     * @return true if successful, false otherwise.
     */
    fun removeAllForPlayer(playerId: UUID): Boolean

    /**
     * Removes all invitations for a guild (e.g., when guild is disbanded).
     *
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun removeAllForGuild(guildId: UUID): Boolean

    /**
     * Checks if a player has a pending invitation from a specific guild.
     *
     * @param playerId The ID of the invited player.
     * @param guildId The ID of the guild.
     * @return true if invitation exists, false otherwise.
     */
    fun hasInvitation(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets the total count of pending invitations for a player.
     *
     * @param playerId The ID of the player.
     * @return The number of pending invitations.
     */
    fun getInvitationCount(playerId: UUID): Int

    /**
     * Removes invitations older than the specified timestamp.
     *
     * @param olderThan Unix timestamp in seconds.
     * @return The number of invitations removed.
     */
    fun removeOlderThan(olderThan: Long): Int
}
