package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RelationStatus
import java.util.UUID

/**
 * Repository interface for managing guild relations persistence.
 */
interface RelationRepository {
    
    /**
     * Adds a new relation to the repository.
     *
     * @param relation The relation to add.
     * @return true if the relation was added successfully, false otherwise.
     */
    fun add(relation: Relation): Boolean
    
    /**
     * Updates an existing relation in the repository.
     *
     * @param relation The relation to update.
     * @return true if the relation was updated successfully, false otherwise.
     */
    fun update(relation: Relation): Boolean
    
    /**
     * Removes a relation from the repository.
     *
     * @param relationId The ID of the relation to remove.
     * @return true if the relation was removed successfully, false otherwise.
     */
    fun remove(relationId: UUID): Boolean
    
    /**
     * Gets a relation by its ID.
     *
     * @param relationId The ID of the relation to retrieve.
     * @return The relation if found, null otherwise.
     */
    fun getById(relationId: UUID): Relation?
    
    /**
     * Gets the relation between two guilds, if one exists.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @return The relation between the guilds if it exists, null otherwise.
     */
    fun getByGuilds(guildA: UUID, guildB: UUID): Relation?
    
    /**
     * Gets all relations for a specific guild.
     *
     * @param guildId The ID of the guild to get relations for.
     * @return A set of relations involving the guild.
     */
    fun getByGuild(guildId: UUID): Set<Relation>
    
    /**
     * Gets relations for a guild filtered by type.
     *
     * @param guildId The ID of the guild.
     * @param type The type of relation to filter by.
     * @return A set of relations of the specified type involving the guild.
     */
    fun getByGuildAndType(guildId: UUID, type: RelationType): Set<Relation>
    
    /**
     * Gets relations for a guild filtered by status.
     *
     * @param guildId The ID of the guild.
     * @param status The status to filter by.
     * @return A set of relations with the specified status involving the guild.
     */
    fun getByGuildAndStatus(guildId: UUID, status: RelationStatus): Set<Relation>
    
    /**
     * Gets all expired relations that need cleanup.
     *
     * @return A set of relations that have expired and should be processed.
     */
    fun getExpiredRelations(): Set<Relation>
    
    /**
     * Gets all active relations of a specific type.
     *
     * @param type The type of relation to retrieve.
     * @return A set of active relations of the specified type.
     */
    fun getByType(type: RelationType): Set<Relation>
    
    /**
     * Gets all relations with a specific status.
     *
     * @param status The status to filter by.
     * @return A set of relations with the specified status.
     */
    fun getByStatus(status: RelationStatus): Set<Relation>
    
    /**
     * Checks if two guilds have a specific type of relation.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @param type The relation type to check for.
     * @return true if the guilds have the specified relation type, false otherwise.
     */
    fun hasRelationType(guildA: UUID, guildB: UUID, type: RelationType): Boolean
    
    /**
     * Gets all relations in the repository.
     *
     * @return A set of all relations.
     */
    fun getAll(): Set<Relation>
}
