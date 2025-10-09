package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarStats
import java.util.UUID

/**
 * Repository interface for managing war persistence.
 */
interface WarRepository {

    /**
     * Adds a new war to the repository.
     *
     * @param war The war to add.
     * @return true if the war was added successfully, false otherwise.
     */
    fun add(war: War): Boolean

    /**
     * Updates an existing war in the repository.
     *
     * @param war The war to update.
     * @return true if the war was updated successfully, false otherwise.
     */
    fun update(war: War): Boolean

    /**
     * Removes a war from the repository.
     *
     * @param warId The ID of the war to remove.
     * @return true if the war was removed successfully, false otherwise.
     */
    fun remove(warId: UUID): Boolean

    /**
     * Gets a war by its ID.
     *
     * @param warId The ID of the war to retrieve.
     * @return The war if found, null otherwise.
     */
    fun getById(warId: UUID): War?

    /**
     * Gets all active wars.
     *
     * @return List of active wars.
     */
    fun getActiveWars(): List<War>

    /**
     * Gets wars for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return List of wars involving the guild.
     */
    fun getWarsForGuild(guildId: UUID): List<War>

    /**
     * Gets the current war between two guilds.
     *
     * @param guildA The first guild.
     * @param guildB The second guild.
     * @return The active war between the guilds, or null if none exists.
     */
    fun getCurrentWarBetweenGuilds(guildA: UUID, guildB: UUID): War?

    /**
     * Gets war history for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit The maximum number of wars to return.
     * @return List of past wars for the guild.
     */
    fun getWarHistory(guildId: UUID, limit: Int = 20): List<War>

    /**
     * Gets all wars in the repository.
     *
     * @return List of all wars.
     */
    fun getAll(): List<War>

    /**
     * Adds a war declaration to the repository.
     *
     * @param declaration The war declaration to add.
     * @return true if added successfully, false otherwise.
     */
    fun addWarDeclaration(declaration: WarDeclaration): Boolean

    /**
     * Updates a war declaration in the repository.
     *
     * @param declaration The war declaration to update.
     * @return true if updated successfully, false otherwise.
     */
    fun updateWarDeclaration(declaration: WarDeclaration): Boolean

    /**
     * Removes a war declaration from the repository.
     *
     * @param declarationId The ID of the declaration to remove.
     * @return true if removed successfully, false otherwise.
     */
    fun removeWarDeclaration(declarationId: UUID): Boolean

    /**
     * Gets a war declaration by its ID.
     *
     * @param declarationId The ID of the declaration.
     * @return The war declaration if found, null otherwise.
     */
    fun getWarDeclarationById(declarationId: UUID): WarDeclaration?

    /**
     * Gets pending war declarations for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of pending declarations that the guild can respond to.
     */
    fun getPendingDeclarationsForGuild(guildId: UUID): List<WarDeclaration>

    /**
     * Gets war declarations sent by a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of declarations sent by the guild.
     */
    fun getDeclarationsByGuild(guildId: UUID): List<WarDeclaration>

    /**
     * Gets expired war declarations that need cleanup.
     *
     * @return List of expired declarations.
     */
    fun getExpiredWarDeclarations(): List<WarDeclaration>

    /**
     * Adds war statistics to the repository.
     *
     * @param stats The war statistics to add.
     * @return true if added successfully, false otherwise.
     */
    fun addWarStats(stats: WarStats): Boolean

    /**
     * Updates war statistics in the repository.
     *
     * @param stats The war statistics to update.
     * @return true if updated successfully, false otherwise.
     */
    fun updateWarStats(stats: WarStats): Boolean

    /**
     * Gets war statistics by war ID.
     *
     * @param warId The ID of the war.
     * @return The war statistics if found, null otherwise.
     */
    fun getWarStatsByWarId(warId: UUID): WarStats?

    /**
     * Gets all war statistics in the repository.
     *
     * @return List of all war statistics.
     */
    fun getAllWarStats(): List<WarStats>

    /**
     * Removes war statistics for a war.
     *
     * @param warId The ID of the war.
     * @return true if removed successfully, false otherwise.
     */
    fun removeWarStats(warId: UUID): Boolean
}
