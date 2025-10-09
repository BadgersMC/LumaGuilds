package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.AuditService
import net.lumalyte.lg.application.persistence.AuditRepository
import net.lumalyte.lg.domain.entities.AuditRecord
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Bukkit implementation of AuditService.
 * 
 * This service provides comprehensive auditing for all key actions in the system,
 * including structured logging with context for better debugging and monitoring.
 */
class AuditServiceBukkit : AuditService, KoinComponent {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val auditRepository: AuditRepository by inject()
    
    override fun recordAction(
        actorId: UUID,
        action: String,
        guildId: UUID?,
        details: String?,
        context: Map<String, Any>?
    ): Boolean {
        return try {
            val auditRecord = AuditRecord(
                id = UUID.randomUUID(),
                time = Instant.now(),
                actorId = actorId,
                guildId = guildId,
                action = action,
                details = details
            )
            
            val success = auditRepository.save(auditRecord)
            
            if (success) {
                // Structured logging with context
                val logContext = buildString {
                    append("action=$action")
                    append(", actor=$actorId")
                    guildId?.let { append(", guild=$it") }
                    details?.let { append(", details=$it") }
                    context?.let { ctx ->
                        ctx.forEach { (key, value) ->
                            append(", $key=$value")
                        }
                    }
                }
                
                logger.info("AUDIT: $logContext")
            }
            
            success
        } catch (e: Exception) {
            logger.error("Failed to record audit action: $action by $actorId", e)
            false
        }
    }
    
    override fun recordGuildAction(
        actorId: UUID,
        guildId: UUID,
        action: String,
        details: String?,
        context: Map<String, Any>?
    ): Boolean {
        return recordAction(actorId, action, guildId, details, context)
    }
    
    override fun recordClaimAction(
        actorId: UUID,
        claimId: UUID,
        action: String,
        details: String?,
        context: Map<String, Any>?
    ): Boolean {
        val enhancedContext = context?.toMutableMap() ?: mutableMapOf()
        enhancedContext["claimId"] = claimId.toString()
        
        return recordAction(actorId, action, null, details, enhancedContext)
    }
    
    override fun recordRankChange(
        actorId: UUID,
        guildId: UUID,
        targetPlayerId: UUID,
        oldRank: String,
        newRank: String,
        reason: String?
    ): Boolean {
        val details = buildString {
            append("Rank change: $oldRank -> $newRank")
            append(" for player $targetPlayerId")
            reason?.let { append(" (reason: $it)") }
        }
        
        val context = mapOf(
            "targetPlayerId" to targetPlayerId.toString(),
            "oldRank" to oldRank,
            "newRank" to newRank,
            "reason" to (reason ?: "none")
        )
        
        return recordGuildAction(actorId, guildId, "RANK_CHANGE", details, context)
    }
    
    override fun recordRelationChange(
        actorId: UUID,
        guildId: UUID,
        targetGuildId: UUID,
        oldRelation: String,
        newRelation: String,
        reason: String?
    ): Boolean {
        val details = buildString {
            append("Relation change: $oldRelation -> $newRelation")
            append(" with guild $targetGuildId")
            reason?.let { append(" (reason: $it)") }
        }
        
        val context = mapOf(
            "targetGuildId" to targetGuildId.toString(),
            "oldRelation" to oldRelation,
            "newRelation" to newRelation,
            "reason" to (reason ?: "none")
        )
        
        return recordGuildAction(actorId, guildId, "RELATION_CHANGE", details, context)
    }
    
    override fun recordWarStateChange(
        actorId: UUID,
        warId: UUID,
        guildId: UUID,
        oldState: String,
        newState: String,
        details: String?
    ): Boolean {
        val actionDetails = buildString {
            append("War state change: $oldState -> $newState")
            details?.let { append(" ($it)") }
        }
        
        val context = mapOf(
            "warId" to warId.toString(),
            "oldState" to oldState,
            "newState" to newState
        )
        
        return recordGuildAction(actorId, guildId, "WAR_STATE_CHANGE", actionDetails, context)
    }
    
    override fun recordGuildModeChange(
        actorId: UUID,
        guildId: UUID,
        oldMode: String,
        newMode: String,
        reason: String?
    ): Boolean {
        val details = buildString {
            append("Guild mode change: $oldMode -> $newMode")
            reason?.let { append(" (reason: $it)") }
        }
        
        val context = mapOf(
            "oldMode" to oldMode,
            "newMode" to newMode,
            "reason" to (reason ?: "none")
        )
        
        return recordGuildAction(actorId, guildId, "GUILD_MODE_CHANGE", details, context)
    }
    
    override fun getAuditForGuild(guildId: UUID, limit: Int?): List<AuditRecord> {
        return try {
            auditRepository.getByGuild(guildId, limit)
        } catch (e: Exception) {
            logger.error("Failed to get audit for guild: $guildId", e)
            emptyList()
        }
    }
    
    override fun getAuditForPlayer(playerId: UUID, limit: Int?): List<AuditRecord> {
        return try {
            auditRepository.getByActor(playerId, limit)
        } catch (e: Exception) {
            logger.error("Failed to get audit for player: $playerId", e)
            emptyList()
        }
    }
    
    override fun getAuditForAction(action: String, limit: Int?): List<AuditRecord> {
        return try {
            auditRepository.getByAction(action, limit)
        } catch (e: Exception) {
            logger.error("Failed to get audit for action: $action", e)
            emptyList()
        }
    }
    
    override fun getAuditInTimeRange(
        startTime: Instant,
        endTime: Instant,
        limit: Int?
    ): List<AuditRecord> {
        return try {
            auditRepository.getInTimeRange(startTime, endTime, limit)
        } catch (e: Exception) {
            logger.error("Failed to get audit in time range: $startTime to $endTime", e)
            emptyList()
        }
    }

    // Item Banking Audit Methods
    override fun recordItemBankingAction(
        actorId: UUID,
        guildId: UUID,
        action: String,
        itemType: String?,
        itemAmount: Int,
        chestId: UUID?,
        details: String?
    ): Boolean {
        return try {
            val detailText = buildString {
                append("Item banking action: $action")
                itemType?.let { append(", Item: $it") }
                if (itemAmount > 0) append(", Amount: $itemAmount")
                chestId?.let { append(", Chest: ${it.toString().take(8)}") }
                details?.let { append(", Details: $it") }
            }

            auditRepository.save(
                AuditRecord(
                    id = UUID.randomUUID(),
                    time = Instant.now(),
                    actorId = actorId,
                    guildId = guildId,
                    action = "ITEM_BANKING_$action",
                    details = detailText
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to record item banking action: $action", e)
            false
        }
    }

    override fun recordChestCreation(
        actorId: UUID,
        guildId: UUID,
        chestId: UUID,
        location: String
    ): Boolean {
        return try {
            auditRepository.save(
                AuditRecord(
                    id = UUID.randomUUID(),
                    time = Instant.now(),
                    actorId = actorId,
                    guildId = guildId,
                    action = "GUILD_CHEST_CREATED",
                    details = "Guild chest created at $location (Chest ID: ${chestId.toString().take(8)})"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to record chest creation", e)
            false
        }
    }

    override fun recordChestDestruction(
        actorId: UUID,
        guildId: UUID,
        chestId: UUID,
        reason: String
    ): Boolean {
        return try {
            auditRepository.save(
                AuditRecord(
                    id = UUID.randomUUID(),
                    time = Instant.now(),
                    actorId = actorId,
                    guildId = guildId,
                    action = "GUILD_CHEST_DESTROYED",
                    details = "Guild chest destroyed: $reason (Chest ID: ${chestId.toString().take(8)})"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to record chest destruction", e)
            false
        }
    }

    override fun recordChestAccess(
        actorId: UUID,
        guildId: UUID,
        chestId: UUID,
        action: String,
        itemType: String?,
        itemAmount: Int
    ): Boolean {
        return try {
            val detailText = buildString {
                append("Guild chest $action performed")
                append(" (Chest ID: ${chestId.toString().take(8)})")
                itemType?.let { append(", Item: $it") }
                if (itemAmount > 0) append(", Amount: $itemAmount")
            }

            auditRepository.save(
                AuditRecord(
                    id = UUID.randomUUID(),
                    time = Instant.now(),
                    actorId = actorId,
                    guildId = guildId,
                    action = "GUILD_CHEST_$action",
                    details = detailText
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to record chest access: $action", e)
            false
        }
    }

    override fun getItemBankingAuditForGuild(guildId: UUID, limit: Int?): List<AuditRecord> {
        return try {
            auditRepository.getByGuild(guildId)
                .filter { it.action.startsWith("ITEM_BANKING_") || it.action.startsWith("GUILD_CHEST_") }
                .let { records ->
                    limit?.let { records.take(it) } ?: records
                }
        } catch (e: Exception) {
            logger.error("Failed to get item banking audit for guild $guildId", e)
            emptyList()
        }
    }

    override fun getSuspiciousItemBankingActivities(limit: Int?): List<AuditRecord> {
        return try {
            auditRepository.getAll()
                .filter {
                    it.action.contains("BREAK_ATTEMPT") ||
                    it.action.contains("UNAUTHORIZED") ||
                    it.action.contains("FAILED_ACCESS")
                }
                .let { records ->
                    limit?.let { records.take(it) } ?: records
                }
        } catch (e: Exception) {
            logger.error("Failed to get suspicious item banking activities", e)
            emptyList()
        }
    }

    override fun getItemBankingAuditInTimeRange(
        startTime: Instant,
        endTime: Instant,
        guildId: UUID?,
        limit: Int?
    ): List<AuditRecord> {
        return try {
            auditRepository.getInTimeRange(startTime, endTime, null)
                .filter { record ->
                    (record.action.startsWith("ITEM_BANKING_") || record.action.startsWith("GUILD_CHEST_")) &&
                    (guildId == null || record.guildId == guildId)
                }
                .let { records ->
                    limit?.let { records.take(it) } ?: records
                }
        } catch (e: Exception) {
            logger.error("Failed to get item banking audit in time range", e)
            emptyList()
        }
    }
}
