package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/**
 * Service interface for managing member operations.
 */
interface MemberService {
    /**
     * Adds a player to a guild.
     *
     * @param playerId The ID of the player to add.
     * @param guildId The ID of the guild.
     * @param rankId The ID of the rank to assign.
     * @return The created member, or null if creation failed.
     */
    fun addMember(playerId: UUID, guildId: UUID, rankId: UUID): Member?

    /**
     * Removes a player from a guild.
     *
     * @param playerId The ID of the player to remove.
     * @param guildId The ID of the guild.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun removeMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean

    /**
     * Changes the rank of a member.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @param newRankId The ID of the new rank.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun changeMemberRank(playerId: UUID, guildId: UUID, newRankId: UUID, actorId: UUID): Boolean

    /**
     * Gets a member by player ID and guild ID.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The member, or null if not found.
     */
    fun getMember(playerId: UUID, guildId: UUID): Member?

    /**
     * Gets all members of a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of members belonging to the guild.
     */
    fun getGuildMembers(guildId: UUID): Set<Member>

    /**
     * Gets all guilds that a player is a member of.
     *
     * @param playerId The ID of the player.
     * @return A set of guild IDs the player belongs to.
     */
    fun getPlayerGuilds(playerId: UUID): Set<UUID>

    /**
     * Gets the rank ID for a player in a specific guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The rank ID, or null if the player is not a member.
     */
    fun getPlayerRankId(playerId: UUID, guildId: UUID): UUID?

    /**
     * Gets all members with a specific rank in a guild.
     *
     * @param guildId The ID of the guild.
     * @param rankId The ID of the rank.
     * @return A set of members with the specified rank.
     */
    fun getMembersByRank(guildId: UUID, rankId: UUID): Set<Member>

    /**
     * Gets the member count for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return The number of members in the guild.
     */
    fun getMemberCount(guildId: UUID): Int

    /**
     * Checks if a player is a member of a specific guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player is a member, false otherwise.
     */
    fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets the total number of members across all guilds.
     *
     * @return The total count of members.
     */
    fun getTotalMemberCount(): Int

    /**
     * Checks if a player has a specific permission in a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @param permission The permission to check.
     * @return true if the player has the permission, false otherwise.
     */
    fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean

    /**
     * Promotes a member to the next highest rank.
     *
     * @param playerId The ID of the player to promote.
     * @param guildId The ID of the guild.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun promoteMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean

    /**
     * Demotes a member to the next lowest rank.
     *
     * @param playerId The ID of the player to demote.
     * @param guildId The ID of the guild.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun demoteMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean
}
