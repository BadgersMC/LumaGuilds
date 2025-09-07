package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Member
import java.util.UUID

/**
 * A repository that handles the persistence of Members.
 */
interface MemberRepository {
    /**
     * Gets all members that exist.
     *
     * @return The set of all members.
     */
    fun getAll(): Set<Member>

    /**
     * Gets a member by player ID and guild ID.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return The found member, or null if it doesn't exist.
     */
    fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): Member?

    /**
     * Gets all members of a specific guild.
     *
     * @param guildId The id of the guild to retrieve members for.
     * @return A set of members belonging to the guild.
     */
    fun getByGuild(guildId: UUID): Set<Member>

    /**
     * Gets all guilds that a player is a member of.
     *
     * @param playerId The id of the player to retrieve guilds for.
     * @return A set of guild IDs the player belongs to.
     */
    fun getGuildsByPlayer(playerId: UUID): Set<UUID>

    /**
     * Gets the rank ID for a player in a specific guild.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return The rank ID, or null if the player is not a member.
     */
    fun getRankId(playerId: UUID, guildId: UUID): UUID?

    /**
     * Gets all members with a specific rank in a guild.
     *
     * @param guildId The id of the guild.
     * @param rankId The id of the rank.
     * @return A set of members with the specified rank.
     */
    fun getByRank(guildId: UUID, rankId: UUID): Set<Member>

    /**
     * Gets the member count for a specific guild.
     *
     * @param guildId The id of the guild.
     * @return The number of members in the guild.
     */
    fun getMemberCount(guildId: UUID): Int

    /**
     * Adds a new member.
     *
     * @param member The member to add.
     * @return true if successful, false otherwise.
     */
    fun add(member: Member): Boolean

    /**
     * Updates the data of an existing member.
     *
     * @param member The member to update.
     * @return true if successful, false otherwise.
     */
    fun update(member: Member): Boolean

    /**
     * Removes an existing member.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return true if successful, false otherwise.
     */
    fun remove(playerId: UUID, guildId: UUID): Boolean

    /**
     * Removes all members from a specific guild.
     *
     * @param guildId The id of the guild.
     * @return true if successful, false otherwise.
     */
    fun removeByGuild(guildId: UUID): Boolean

    /**
     * Checks if a player is a member of a specific guild.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return true if the player is a member, false otherwise.
     */
    fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets the total number of members across all guilds.
     *
     * @return The total count of members.
     */
    fun getTotalCount(): Int
}
