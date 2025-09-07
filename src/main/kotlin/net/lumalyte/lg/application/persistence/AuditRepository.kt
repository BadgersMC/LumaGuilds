package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.AuditRecord
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for audit record persistence.
 * 
 * This repository handles:
 * - Storing audit records for all key actions
 * - Retrieving audit trails for investigation
 * - Filtering by various criteria (guild, player, action, time)
 */
interface AuditRepository {
    
    /**
     * Saves an audit record.
     *
     * @param auditRecord The audit record to save.
     * @return true if successful, false otherwise.
     */
    fun save(auditRecord: AuditRecord): Boolean
    
    /**
     * Gets audit records for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of audit records for the guild.
     */
    fun getByGuild(guildId: UUID, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records for a specific actor (player).
     *
     * @param actorId The ID of the player.
     * @param limit Optional limit for the number of results.
     * @return List of audit records for the player.
     */
    fun getByActor(actorId: UUID, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records for a specific action type.
     *
     * @param action The action type to filter by.
     * @param limit Optional limit for the number of results.
     * @return List of audit records for the action type.
     */
    fun getByAction(action: String, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records within a time range.
     *
     * @param startTime The start time for the range.
     * @param endTime The end time for the range.
     * @param limit Optional limit for the number of results.
     * @return List of audit records within the time range.
     */
    fun getInTimeRange(startTime: Instant, endTime: Instant, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records by ID.
     *
     * @param id The ID of the audit record.
     * @return The audit record, or null if not found.
     */
    fun getById(id: UUID): AuditRecord?
    
    /**
     * Gets all audit records with optional filtering.
     *
     * @param limit Optional limit for the number of results.
     * @return List of all audit records.
     */
    fun getAll(limit: Int? = null): List<AuditRecord>
    
    /**
     * Deletes audit records older than a specified time.
     *
     * @param cutoffTime Records older than this time will be deleted.
     * @return The number of records deleted.
     */
    fun deleteOlderThan(cutoffTime: Instant): Int
    
    /**
     * Gets the count of audit records for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return The count of audit records for the guild.
     */
    fun getCountByGuild(guildId: UUID): Int
    
    /**
     * Gets the count of audit records for a specific actor.
     *
     * @param actorId The ID of the actor.
     * @return The count of audit records for the actor.
     */
    fun getCountByActor(actorId: UUID): Int
    
    /**
     * Gets the count of audit records for a specific action.
     *
     * @param action The action type.
     * @return The count of audit records for the action.
     */
    fun getCountByAction(action: String): Int
}
