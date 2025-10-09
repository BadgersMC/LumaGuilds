package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RankInheritance
import java.util.UUID

/**
 * Service interface for managing rank operations.
 */
interface RankService {
    /**
     * Lists all ranks for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of ranks belonging to the guild.
     */
    fun listRanks(guildId: UUID): Set<Rank>

    /**
     * Adds a new rank to a guild.
     *
     * @param guildId The ID of the guild.
     * @param name The name of the rank.
     * @param permissions The permissions for the rank.
     * @param actorId The ID of the player performing the action.
     * @return The created rank, or null if creation failed.
     */
    fun addRank(guildId: UUID, name: String, permissions: Set<RankPermission> = emptySet(), actorId: UUID): Rank?

    /**
     * Renames an existing rank.
     *
     * @param rankId The ID of the rank to rename.
     * @param newName The new name for the rank.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun renameRank(rankId: UUID, newName: String, actorId: UUID): Boolean

    /**
     * Deletes a rank from a guild.
     *
     * @param rankId The ID of the rank to delete.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun deleteRank(rankId: UUID, actorId: UUID): Boolean

    /**
     * Sets the permissions for a rank.
     *
     * @param rankId The ID of the rank.
     * @param permissions The permissions to set.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setRankPermissions(rankId: UUID, permissions: Set<RankPermission>, actorId: UUID): Boolean

    /**
     * Adds a permission to a rank.
     *
     * @param rankId The ID of the rank.
     * @param permission The permission to add.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun addRankPermission(rankId: UUID, permission: RankPermission, actorId: UUID): Boolean

    /**
     * Removes a permission from a rank.
     *
     * @param rankId The ID of the rank.
     * @param permission The permission to remove.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun removeRankPermission(rankId: UUID, permission: RankPermission, actorId: UUID): Boolean

    /**
     * Assigns a rank to a member.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @param rankId The ID of the rank to assign.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun assignRank(playerId: UUID, guildId: UUID, rankId: UUID, actorId: UUID): Boolean

    /**
     * Gets the rank of a player in a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The rank of the player, or null if not found.
     */
    fun getPlayerRank(playerId: UUID, guildId: UUID): Rank?

    /**
     * Gets a rank by ID.
     *
     * @param rankId The ID of the rank.
     * @return The rank, or null if not found.
     */
    fun getRank(rankId: UUID): Rank?

    /**
     * Gets a rank by ID with guild validation.
     *
     * @param guildId The ID of the guild for validation.
     * @param rankId The ID of the rank.
     * @return The rank, or null if not found or doesn't belong to guild.
     */
    fun getRankById(guildId: UUID, rankId: UUID): Rank?

    /**
     * Gets a rank by ID (alias for backward compatibility).
     *
     * @param rankId The ID of the rank.
     * @return The rank, or null if not found.
     */
    fun getRankById(rankId: UUID): Rank?

    /**
     * Gets all ranks for a guild (alias for listRanks).
     *
     * @param guildId The ID of the guild.
     * @return A set of ranks belonging to the guild.
     */
    fun getGuildRanks(guildId: UUID): Set<Rank>

    /**
     * Gets all ranks by guild ID (alias for listRanks).
     *
     * @param guildId The ID of the guild.
     * @return A set of ranks belonging to the guild.
     */
    fun getRanksByGuild(guildId: UUID): Set<Rank>

    /**
     * Gets a rank by name within a guild.
     *
     * @param guildId The ID of the guild.
     * @param name The name of the rank.
     * @return The rank, or null if not found.
     */
    fun getRankByName(guildId: UUID, name: String): Rank?

    /**
     * Gets the default rank for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The default rank, or null if none exists.
     */
    fun getDefaultRank(guildId: UUID): Rank?

    /**
     * Gets the highest priority rank for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The highest priority rank, or null if none exists.
     */
    fun getHighestRank(guildId: UUID): Rank?

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
     * Gets the total number of ranks for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The total count of ranks.
     */
    fun getRankCount(guildId: UUID): Int

    /**
     * Updates an existing rank with new data.
     *
     * @param rank The updated rank object.
     * @return true if successful, false otherwise.
     */
    fun updateRank(rank: Rank): Boolean

    /**
     * Creates default ranks for a new guild.
     *
     * @param guildId The ID of the guild.
     * @param ownerId The ID of the guild owner.
     * @return true if successful, false otherwise.
     */
    fun createDefaultRanks(guildId: UUID, ownerId: UUID): Boolean

    /**
     * Gets the inheritance information for a rank.
     *
     * @param rankId The ID of the rank.
     * @return The inheritance information, or null if not found.
     */
    fun getRankInheritance(rankId: UUID): RankInheritance?

    /**
     * Adds inheritance from a parent rank to a child rank.
     *
     * @param childRankId The ID of the child rank.
     * @param parentRankId The ID of the parent rank.
     * @return true if successful, false otherwise.
     */
    fun addRankInheritance(childRankId: UUID, parentRankId: UUID): Boolean

    /**
     * Removes inheritance from a parent rank to a child rank.
     *
     * @param childRankId The ID of the child rank.
     * @param parentRankId The ID of the parent rank.
     * @return true if successful, false otherwise.
     */
    fun removeRankInheritance(childRankId: UUID, parentRankId: UUID): Boolean

    /**
     * Gets all child ranks for a given parent rank.
     *
     * @param parentRankId The ID of the parent rank.
     * @return A set of child ranks.
     */
    fun getChildRanks(parentRankId: UUID): Set<Rank>

    /**
     * Gets all parent ranks for a given child rank.
     *
     * @param childRankId The ID of the child rank.
     * @return A set of parent ranks.
     */
    fun getParentRanks(childRankId: UUID): Set<Rank>

    /**
     * Validates that adding inheritance won't create circular dependencies.
     *
     * @param childRankId The ID of the child rank.
     * @param parentRankId The ID of the parent rank.
     * @return true if inheritance is valid, false if it would create circular dependency.
     */
    fun validateInheritance(childRankId: UUID, parentRankId: UUID): Boolean
}
