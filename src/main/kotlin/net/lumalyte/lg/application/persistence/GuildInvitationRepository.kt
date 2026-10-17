package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildInvitation
import net.lumalyte.lg.domain.entities.GuildInvitationLeaderboardEntry
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

    /** Returns the all-time sent-invitation leaderboard for one guild. */
    fun getInvitationLeaderboard(guildId: UUID, limit: Int): List<GuildInvitationLeaderboardEntry>

    /** Returns one deterministic page of the all-time invitation leaderboard. */
    fun getInvitationLeaderboardPage(guildId: UUID, offset: Int, limit: Int): List<GuildInvitationLeaderboardEntry>

    /** Returns the number of distinct inviters represented in the all-time leaderboard. */
    fun getInvitationLeaderboardInviterCount(guildId: UUID): Int

    /** Returns the all-time number of successfully persisted invitations sent by this guild. */
    fun getSentInvitationCount(guildId: UUID): Int
}
