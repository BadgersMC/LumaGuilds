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
}
