package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.AuditRecord
import java.util.UUID

/**
 * Service interface for comprehensive auditing of key actions in the system.
 * 
 * This service handles:
 * - Recording audit entries for all important operations
 * - Structured logging with context
 * - Audit trail retrieval for investigation
 * - Integration with existing bank audit system
 */
interface AuditService {
    
    /**
     * Records an audit entry for a key action.
     *
     * @param actorId The ID of the player who performed the action.
     * @param action The type of action performed.
     * @param guildId Optional guild ID if the action involves a guild.
     * @param details Additional details about the action.
     * @param context Optional context data for structured logging.
     * @return true if successful, false otherwise.
     */
    fun recordAction(
        actorId: UUID,
        action: String,
        guildId: UUID? = null,
        details: String? = null,
        context: Map<String, Any>? = null
    ): Boolean
    
    /**
     * Records a guild-related action with full context.
     *
     * @param actorId The ID of the player who performed the action.
     * @param guildId The ID of the guild involved.
     * @param action The type of action performed.
     * @param details Additional details about the action.
     * @param context Optional context data for structured logging.
     * @return true if successful, false otherwise.
     */
    fun recordGuildAction(
        actorId: UUID,
        guildId: UUID,
        action: String,
        details: String? = null,
        context: Map<String, Any>? = null
    ): Boolean
    
    /**
     * Records a claim-related action.
     *
     * @param actorId The ID of the player who performed the action.
     * @param claimId The ID of the claim involved.
     * @param action The type of action performed.
     * @param details Additional details about the action.
     * @param context Optional context data for structured logging.
     * @return true if successful, false otherwise.
     */
    fun recordClaimAction(
        actorId: UUID,
        claimId: UUID,
        action: String,
        details: String? = null,
        context: Map<String, Any>? = null
    ): Boolean
    
    /**
     * Records a rank change action.
     *
     * @param actorId The ID of the player who performed the action.
     * @param guildId The ID of the guild.
     * @param targetPlayerId The ID of the player whose rank changed.
     * @param oldRank The previous rank.
     * @param newRank The new rank.
     * @param reason Optional reason for the change.
     * @return true if successful, false otherwise.
     */
    fun recordRankChange(
        actorId: UUID,
        guildId: UUID,
        targetPlayerId: UUID,
        oldRank: String,
        newRank: String,
        reason: String? = null
    ): Boolean
    
    /**
     * Records a relation change action.
     *
     * @param actorId The ID of the player who performed the action.
     * @param guildId The ID of the guild.
     * @param targetGuildId The ID of the target guild.
     * @param oldRelation The previous relation type.
     * @param newRelation The new relation type.
     * @param reason Optional reason for the change.
     * @return true if successful, false otherwise.
     */
    fun recordRelationChange(
        actorId: UUID,
        guildId: UUID,
        targetGuildId: UUID,
        oldRelation: String,
        newRelation: String,
        reason: String? = null
    ): Boolean
    
    /**
     * Records a war state change.
     *
     * @param actorId The ID of the player who performed the action.
     * @param warId The ID of the war.
     * @param guildId The ID of the guild.
     * @param oldState The previous war state.
     * @param newState The new war state.
     * @param details Additional details about the change.
     * @return true if successful, false otherwise.
     */
    fun recordWarStateChange(
        actorId: UUID,
        warId: UUID,
        guildId: UUID,
        oldState: String,
        newState: String,
        details: String? = null
    ): Boolean
    
    /**
     * Records a guild mode change.
     *
     * @param actorId The ID of the player who performed the action.
     * @param guildId The ID of the guild.
     * @param oldMode The previous mode.
     * @param newMode The new mode.
     * @param reason Optional reason for the change.
     * @return true if successful, false otherwise.
     */
    fun recordGuildModeChange(
        actorId: UUID,
        guildId: UUID,
        oldMode: String,
        newMode: String,
        reason: String? = null
    ): Boolean
    
    /**
     * Gets audit records for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of audit records for the guild.
     */
    fun getAuditForGuild(guildId: UUID, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records for a specific player.
     *
     * @param playerId The ID of the player.
     * @param limit Optional limit for the number of results.
     * @return List of audit records for the player.
     */
    fun getAuditForPlayer(playerId: UUID, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records for a specific action type.
     *
     * @param action The action type to filter by.
     * @param limit Optional limit for the number of results.
     * @return List of audit records for the action type.
     */
    fun getAuditForAction(action: String, limit: Int? = null): List<AuditRecord>
    
    /**
     * Gets audit records within a time range.
     *
     * @param startTime The start time for the range.
     * @param endTime The end time for the range.
     * @param limit Optional limit for the number of results.
     * @return List of audit records within the time range.
     */
    fun getAuditInTimeRange(startTime: java.time.Instant, endTime: java.time.Instant, limit: Int? = null): List<AuditRecord>

    /**
     * Records an item banking action.
     *
     * @param actorId The ID of the player who performed the action.
     * @param guildId The ID of the guild involved.
     * @param action The type of banking action.
     * @param itemType The type of item involved (if applicable).
     * @param itemAmount The amount of items involved (if applicable).
     * @param chestId The ID of the chest involved (if applicable).
     * @param details Additional details about the action.
     * @return true if successful, false otherwise.
     */
    fun recordItemBankingAction(
        actorId: UUID,
        guildId: UUID,
        action: String,
        itemType: String? = null,
        itemAmount: Int = 0,
        chestId: UUID? = null,
        details: String? = null
    ): Boolean

    /**
     * Records a guild chest creation.
     *
     * @param actorId The ID of the player who created the chest.
     * @param guildId The ID of the guild.
     * @param chestId The ID of the created chest.
     * @param location The location of the chest.
     * @return true if successful, false otherwise.
     */
    fun recordChestCreation(
        actorId: UUID,
        guildId: UUID,
        chestId: UUID,
        location: String
    ): Boolean

    /**
     * Records a guild chest destruction.
     *
     * @param actorId The ID of the player who destroyed the chest.
     * @param guildId The ID of the guild that owned the chest.
     * @param chestId The ID of the destroyed chest.
     * @param reason The reason for destruction.
     * @return true if successful, false otherwise.
     */
    fun recordChestDestruction(
        actorId: UUID,
        guildId: UUID,
        chestId: UUID,
        reason: String
    ): Boolean

    /**
     * Records a guild chest access.
     *
     * @param actorId The ID of the player who accessed the chest.
     * @param guildId The ID of the guild.
     * @param chestId The ID of the chest.
     * @param action The type of access (OPEN, DEPOSIT, WITHDRAW).
     * @param itemType The type of item involved (if applicable).
     * @param itemAmount The amount of items involved (if applicable).
     * @return true if successful, false otherwise.
     */
    fun recordChestAccess(
        actorId: UUID,
        guildId: UUID,
        chestId: UUID,
        action: String,
        itemType: String? = null,
        itemAmount: Int = 0
    ): Boolean

    /**
     * Gets audit records for a specific guild's item banking activities.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of item banking audit records for the guild.
     */
    fun getItemBankingAuditForGuild(guildId: UUID, limit: Int? = null): List<AuditRecord>

    /**
     * Gets audit records for suspicious item banking activities.
     *
     * @param limit Optional limit for the number of results.
     * @return List of suspicious item banking audit records.
     */
    fun getSuspiciousItemBankingActivities(limit: Int? = null): List<AuditRecord>

    /**
     * Gets audit records for item banking transactions within a time range.
     *
     * @param startTime The start time for the range.
     * @param endTime The end time for the range.
     * @param guildId Optional guild ID to filter by.
     * @param limit Optional limit for the number of results.
     * @return List of item banking audit records within the time range.
     */
    fun getItemBankingAuditInTimeRange(
        startTime: java.time.Instant,
        endTime: java.time.Instant,
        guildId: UUID? = null,
        limit: Int? = null
    ): List<AuditRecord>
}
