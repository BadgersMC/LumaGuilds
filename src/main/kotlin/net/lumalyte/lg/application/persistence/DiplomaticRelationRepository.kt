package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DiplomaticRelation
import net.lumalyte.lg.domain.entities.DiplomaticRelationType
import java.util.UUID

/**
 * Repository interface for managing diplomatic relations persistence.
 */
interface DiplomaticRelationRepository {

    /**
     * Adds a new diplomatic relation to the repository.
     *
     * @param relation The relation to add.
     * @return true if the relation was added successfully, false otherwise.
     */
    fun add(relation: DiplomaticRelation): Boolean

    /**
     * Updates an existing diplomatic relation in the repository.
     *
     * @param relation The relation to update.
     * @return true if the relation was updated successfully, false otherwise.
     */
    fun update(relation: DiplomaticRelation): Boolean

    /**
     * Removes a diplomatic relation from the repository.
     *
     * @param relationId The ID of the relation to remove.
     * @return true if the relation was removed successfully, false otherwise.
     */
    fun remove(relationId: UUID): Boolean

    /**
     * Gets a diplomatic relation by its ID.
     *
     * @param relationId The ID of the relation to retrieve.
     * @return The relation if found, null otherwise.
     */
    fun getById(relationId: UUID): DiplomaticRelation?

    /**
     * Gets the diplomatic relation between two guilds, if one exists.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @return The relation between the guilds if it exists, null otherwise.
     */
    fun getByGuilds(guildA: UUID, guildB: UUID): DiplomaticRelation?

    /**
     * Gets all diplomatic relations for a specific guild.
     *
     * @param guildId The ID of the guild to get relations for.
     * @return A list of relations involving the guild.
     */
    fun getByGuild(guildId: UUID): List<DiplomaticRelation>

    /**
     * Gets diplomatic relations for a guild filtered by type.
     *
     * @param guildId The ID of the guild.
     * @param type The type of relation to filter by.
     * @return A list of relations of the specified type involving the guild.
     */
    fun getByGuildAndType(guildId: UUID, type: DiplomaticRelationType): List<DiplomaticRelation>

    /**
     * Gets all active diplomatic relations for a specific guild.
     *
     * @param guildId The ID of the guild to get relations for.
     * @return A list of active relations involving the guild.
     */
    fun getActiveRelations(guildId: UUID): List<DiplomaticRelation>

    /**
     * Gets all expired diplomatic relations that need cleanup.
     *
     * @return A list of relations that have expired and should be processed.
     */
    fun getExpiredRelations(): List<DiplomaticRelation>

    /**
     * Gets diplomatic relations for a guild filtered by type and active status.
     *
     * @param guildId The ID of the guild.
     * @param type The type of relation to filter by.
     * @return A list of active relations of the specified type involving the guild.
     */
    fun getActiveRelationsByType(guildId: UUID, type: DiplomaticRelationType): List<DiplomaticRelation>

    /**
     * Checks if two guilds have a specific type of diplomatic relation.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @param type The relation type to check for.
     * @return true if the guilds have the specified relation type, false otherwise.
     */
    fun hasRelationType(guildA: UUID, guildB: UUID, type: DiplomaticRelationType): Boolean

    /**
     * Gets all diplomatic relations in the repository.
     *
     * @return A list of all relations.
     */
    fun getAll(): List<DiplomaticRelation>
}
