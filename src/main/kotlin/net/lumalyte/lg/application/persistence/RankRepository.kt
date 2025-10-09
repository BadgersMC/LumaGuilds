package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Rank
import java.util.UUID

/**
 * A repository that handles the persistence of Ranks.
 */
interface RankRepository {
    /**
     * Gets all ranks that exist.
     *
     * @return The set of all ranks.
     */
    fun getAll(): Set<Rank>

    /**
     * Gets a rank by its id.
     *
     * @param id the unique id of the rank.
     * @return The found rank, or null if it doesn't exist.
     */
    fun getById(id: UUID): Rank?

    /**
     * Gets all ranks for a specific guild.
     *
     * @param guildId The id of the guild to retrieve ranks for.
     * @return A set of ranks belonging to the guild.
     */
    fun getByGuild(guildId: UUID): Set<Rank>

    /**
     * Gets a rank by name within a guild.
     *
     * @param guildId The id of the guild.
     * @param name The name of the rank.
     * @return The found rank, or null if it doesn't exist.
     */
    fun getByName(guildId: UUID, name: String): Rank?

    /**
     * Gets the default rank for a guild (usually the lowest priority).
     *
     * @param guildId The id of the guild.
     * @return The default rank, or null if none exists.
     */
    fun getDefaultRank(guildId: UUID): Rank?

    /**
     * Gets the highest priority rank for a guild.
     *
     * @param guildId The id of the guild.
     * @return The highest priority rank, or null if none exists.
     */
    fun getHighestRank(guildId: UUID): Rank?

    /**
     * Adds a new rank.
     *
     * @param rank The rank to add.
     * @return true if successful, false otherwise.
     */
    fun add(rank: Rank): Boolean

    /**
     * Updates the data of an existing rank.
     *
     * @param rank The rank to update.
     * @return true if successful, false otherwise.
     */
    fun update(rank: Rank): Boolean

    /**
     * Removes an existing rank.
     *
     * @param rankId The id of the rank to remove.
     * @return true if successful, false otherwise.
     */
    fun remove(rankId: UUID): Boolean

    /**
     * Removes all ranks for a specific guild.
     *
     * @param guildId The id of the guild.
     * @return true if successful, false otherwise.
     */
    fun removeByGuild(guildId: UUID): Boolean

    /**
     * Checks if a rank name is already taken within a guild.
     *
     * @param guildId The id of the guild.
     * @param name The name to check.
     * @return true if the name is taken, false otherwise.
     */
    fun isNameTaken(guildId: UUID, name: String): Boolean

    /**
     * Gets the next available priority for a guild.
     *
     * @param guildId The id of the guild.
     * @return The next available priority number.
     */
    fun getNextPriority(guildId: UUID): Int

    /**
     * Gets the total number of ranks for a guild.
     *
     * @param guildId The id of the guild.
     * @return The total count of ranks.
     */
    fun getCountByGuild(guildId: UUID): Int

    /**
     * Adds an inheritance relationship between two ranks.
     *
     * @param childRankId The ID of the child rank.
     * @param parentRankId The ID of the parent rank.
     * @return true if successful, false otherwise.
     */
    fun addInheritance(childRankId: UUID, parentRankId: UUID): Boolean

    /**
     * Removes an inheritance relationship between two ranks.
     *
     * @param childRankId The ID of the child rank.
     * @param parentRankId The ID of the parent rank.
     * @return true if successful, false otherwise.
     */
    fun removeInheritance(childRankId: UUID, parentRankId: UUID): Boolean

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
}
