package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RankChangeRecord
import java.time.Instant
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

    // === ADVANCED MEMBER SEARCH ===

    /**
     * Searches for members in a guild using advanced filters.
     *
     * @param guildId The ID of the guild.
     * @param filter The search filter criteria.
     * @return List of matching members.
     */
    fun searchMembers(guildId: UUID, filter: MemberSearchFilter): List<Member>

    /**
     * Gets member activity statistics.
     *
     * @param guildId The ID of the guild.
     * @param memberId The ID of the member.
     * @param periodDays Number of days to analyze.
     * @return Member activity statistics.
     */
    fun getMemberActivityStats(guildId: UUID, memberId: UUID, periodDays: Int = 30): MemberActivityStats

    /**
     * Gets members by activity level.
     *
     * @param guildId The ID of the guild.
     * @param activityLevel The activity level to filter by.
     * @return List of members matching the activity level.
     */
    fun getMembersByActivityLevel(guildId: UUID, activityLevel: ActivityLevel): List<Member>

    // === BULK MEMBER OPERATIONS ===

    /**
     * Performs bulk rank changes on multiple members.
     *
     * @param guildId The ID of the guild.
     * @param memberIds List of member IDs to update.
     * @param newRankId The new rank ID to assign.
     * @param actorId The ID of the player performing the action.
     * @return Number of members successfully updated.
     */
    fun bulkChangeRank(guildId: UUID, memberIds: List<UUID>, newRankId: UUID, actorId: UUID): Int

    /**
     * Sends a message to multiple members.
     *
     * @param guildId The ID of the guild.
     * @param memberIds List of member IDs to message.
     * @param message The message to send.
     * @param senderId The ID of the player sending the message.
     * @return Number of members successfully messaged.
     */
    fun bulkMessageMembers(guildId: UUID, memberIds: List<UUID>, message: String, senderId: UUID): Int

    /**
     * Gets members grouped by rank.
     *
     * @param guildId The ID of the guild.
     * @return Map of rank ID to list of members.
     */
    fun getMembersGroupedByRank(guildId: UUID): Map<UUID, List<Member>>

    /**
     * Gets inactive members (no activity for specified days).
     *
     * @param guildId The ID of the guild.
     * @param inactiveDays Number of days without activity.
     * @return List of inactive members.
     */
    fun getInactiveMembers(guildId: UUID, inactiveDays: Int = 30): List<Member>

    /**
     * Gets online members of a guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of online members.
     */
    fun getOnlineMembers(guildId: UUID): Set<Member>

    /**
     * Gets notes for a member.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The member notes, or empty string if none exist.
     */
    fun getMemberNotes(playerId: UUID, guildId: UUID): String

    /**
     * Sets notes for a member.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @param notes The notes to set.
     * @param actorId The ID of the player setting the notes.
     * @return true if successful, false otherwise.
     */
    fun setMemberNotes(playerId: UUID, guildId: UUID, notes: String, actorId: UUID): Boolean

    /**
     * Gets rank change history for a member.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return List of rank change records, ordered by most recent first.
     */
    fun getRankChangeHistory(playerId: UUID, guildId: UUID): List<net.lumalyte.lg.domain.entities.RankChangeRecord>
}

/**
 * Filter criteria for advanced member search.
 */
data class MemberSearchFilter(
    val nameQuery: String? = null,
    val rankFilter: Set<UUID>? = null,
    val onlineOnly: Boolean = false,
    val joinDateAfter: Instant? = null,
    val joinDateBefore: Instant? = null,
    val activityLevel: ActivityLevel? = null,
    val minContributions: Int? = null,
    val maxContributions: Int? = null
)

/**
 * Activity levels for member classification.
 */
enum class ActivityLevel {
    HIGH,
    MEDIUM,
    LOW,
    INACTIVE
}

/**
 * Member activity statistics for a given period.
 */
data class MemberActivityStats(
    val memberId: UUID,
    val guildId: UUID,
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalContributions: Int,
    val totalWithdrawals: Int,
    val netContribution: Int,
    val transactionCount: Int,
    val averageTransactionAmount: Double,
    val lastActivityDate: Instant?,
    val activityLevel: ActivityLevel,
    val rankChanges: Int,
    val daysActive: Int
)
