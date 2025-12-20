package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.events.GuildRelationChangeEvent
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RelationServiceBukkit(
    private val relationRepository: RelationRepository,
    private val memberService: MemberService
) : RelationService {
    
    private val logger = LoggerFactory.getLogger(RelationServiceBukkit::class.java)
    
    override fun requestAlliance(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, requestingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $requestingGuildId")
                return null
            }
            
            // Validate the relation change
            if (!isValidRelationChange(requestingGuildId, targetGuildId, RelationType.ALLY)) {
                logger.warn("Invalid alliance request from guild $requestingGuildId to guild $targetGuildId")
                return null
            }
            
            // Check for existing relation
            val existingRelation = relationRepository.getByGuilds(requestingGuildId, targetGuildId)
            if (existingRelation != null) {
                logger.warn("Relation already exists between guilds $requestingGuildId and $targetGuildId")
                return null
            }
            
            // Create pending alliance request
            val relation = Relation.create(
                guildA = requestingGuildId,
                guildB = targetGuildId,
                type = RelationType.ALLY,
                status = RelationStatus.PENDING
            )
            
            return if (relationRepository.add(relation)) {
                logger.info("Alliance request created from guild $requestingGuildId to guild $targetGuildId")
                relation
            } else {
                logger.error("Failed to save alliance request")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error creating alliance request", e)
            return null
        }
    }
    
    override fun acceptAlliance(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, acceptingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $acceptingGuildId")
                return null
            }
            
            val relation = relationRepository.getById(relationId)
            if (relation == null) {
                logger.warn("Relation $relationId not found")
                return null
            }
            
            // Validate the accepting guild is part of the relation
            if (!relation.involves(acceptingGuildId)) {
                logger.warn("Guild $acceptingGuildId is not part of relation $relationId")
                return null
            }
            
            // Validate the relation is pending and of the correct type
            if (relation.status != RelationStatus.PENDING || relation.type != RelationType.ALLY) {
                logger.warn("Relation $relationId is not a pending alliance request")
                return null
            }
            
            // Update relation to active
            val updatedRelation = relation.copy(
                status = RelationStatus.ACTIVE,
                updatedAt = Instant.now()
            )
            
            return if (relationRepository.update(updatedRelation)) {
                logger.info("Alliance accepted between guilds ${relation.guildA} and ${relation.guildB}")
                // Fire relation change event
                Bukkit.getPluginManager().callEvent(
                    GuildRelationChangeEvent(relation.guildA, relation.guildB, RelationType.ALLY, updatedRelation)
                )
                updatedRelation
            } else {
                logger.error("Failed to update alliance relation")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error accepting alliance", e)
            return null
        }
    }
    
    override fun declareWar(declaringGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, declaringGuildId)) {
                logger.warn("Player $actorId does not have permission to declare war for guild $declaringGuildId")
                return null
            }
            
            // Check for existing relation
            val existingRelation = relationRepository.getByGuilds(declaringGuildId, targetGuildId)
            
            val relation = if (existingRelation != null) {
                // Update existing relation to enemy
                existingRelation.copy(
                    type = RelationType.ENEMY,
                    status = RelationStatus.ACTIVE,
                    expiresAt = null,
                    updatedAt = Instant.now()
                )
            } else {
                // Create new enemy relation
                Relation.create(
                    guildA = declaringGuildId,
                    guildB = targetGuildId,
                    type = RelationType.ENEMY,
                    status = RelationStatus.ACTIVE
                )
            }
            
            val success = if (existingRelation != null) {
                relationRepository.update(relation)
            } else {
                relationRepository.add(relation)
            }
            
            return if (success) {
                logger.info("War declared between guilds ${relation.guildA} and ${relation.guildB}")
                // Fire relation change event
                Bukkit.getPluginManager().callEvent(
                    GuildRelationChangeEvent(relation.guildA, relation.guildB, RelationType.ENEMY, relation)
                )
                relation
            } else {
                logger.error("Failed to save enemy relation")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error declaring war", e)
            return null
        }
    }
    
    override fun requestTruce(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID, duration: Duration): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, requestingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $requestingGuildId")
                return null
            }
            
            // Check that guilds are currently enemies
            val currentRelation = relationRepository.getByGuilds(requestingGuildId, targetGuildId)
            if (currentRelation?.type != RelationType.ENEMY || !currentRelation.isActive()) {
                logger.warn("Cannot request truce - guilds are not currently at war")
                return null
            }
            
            val expiresAt = Instant.now().plus(duration)
            
            // Update existing relation to pending truce
            val truceRelation = currentRelation.copy(
                type = RelationType.TRUCE,
                status = RelationStatus.PENDING,
                expiresAt = expiresAt,
                updatedAt = Instant.now()
            )
            
            return if (relationRepository.update(truceRelation)) {
                logger.info("Truce request created from guild $requestingGuildId to guild $targetGuildId")
                truceRelation
            } else {
                logger.error("Failed to save truce request")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error creating truce request", e)
            return null
        }
    }
    
    override fun acceptTruce(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, acceptingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $acceptingGuildId")
                return null
            }
            
            val relation = relationRepository.getById(relationId)
            if (relation == null) {
                logger.warn("Relation $relationId not found")
                return null
            }
            
            // Validate the accepting guild is part of the relation
            if (!relation.involves(acceptingGuildId)) {
                logger.warn("Guild $acceptingGuildId is not part of relation $relationId")
                return null
            }
            
            // Validate the relation is pending and of the correct type
            if (relation.status != RelationStatus.PENDING || relation.type != RelationType.TRUCE) {
                logger.warn("Relation $relationId is not a pending truce request")
                return null
            }
            
            // Update relation to active
            val updatedRelation = relation.copy(
                status = RelationStatus.ACTIVE,
                updatedAt = Instant.now()
            )
            
            return if (relationRepository.update(updatedRelation)) {
                logger.info("Truce accepted between guilds ${relation.guildA} and ${relation.guildB}")
                // Fire relation change event
                Bukkit.getPluginManager().callEvent(
                    GuildRelationChangeEvent(relation.guildA, relation.guildB, RelationType.TRUCE, updatedRelation)
                )
                updatedRelation
            } else {
                logger.error("Failed to update truce relation")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error accepting truce", e)
            return null
        }
    }
    
    override fun requestUnenemy(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, requestingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $requestingGuildId")
                return null
            }
            
            // Check that guilds are currently enemies
            val currentRelation = relationRepository.getByGuilds(requestingGuildId, targetGuildId)
            if (currentRelation?.type != RelationType.ENEMY || !currentRelation.isActive()) {
                logger.warn("Cannot request unenemy - guilds are not currently at war")
                return null
            }
            
            // Update existing relation to pending neutral request
            val unEnemyRelation = currentRelation.copy(
                type = RelationType.NEUTRAL,
                status = RelationStatus.PENDING,
                expiresAt = null,
                updatedAt = Instant.now()
            )
            
            return if (relationRepository.update(unEnemyRelation)) {
                logger.info("Unenemy request created from guild $requestingGuildId to guild $targetGuildId")
                unEnemyRelation
            } else {
                logger.error("Failed to save unenemy request")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error creating unenemy request", e)
            return null
        }
    }
    
    override fun acceptUnenemy(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation? {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, acceptingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $acceptingGuildId")
                return null
            }
            
            val relation = relationRepository.getById(relationId)
            if (relation == null) {
                logger.warn("Relation $relationId not found")
                return null
            }
            
            // Validate the accepting guild is part of the relation
            if (!relation.involves(acceptingGuildId)) {
                logger.warn("Guild $acceptingGuildId is not part of relation $relationId")
                return null
            }
            
            // Validate the relation is pending neutral request
            if (relation.status != RelationStatus.PENDING || relation.type != RelationType.NEUTRAL) {
                logger.warn("Relation $relationId is not a pending unenemy request")
                return null
            }
            
            // Remove the relation (neutral is the default state)
            return if (relationRepository.remove(relationId)) {
                logger.info("Unenemy accepted, relation removed between guilds ${relation.guildA} and ${relation.guildB}")
                val neutralRelation = relation.copy(status = RelationStatus.ACTIVE)
                // Fire relation change event
                Bukkit.getPluginManager().callEvent(
                    GuildRelationChangeEvent(relation.guildA, relation.guildB, RelationType.NEUTRAL, neutralRelation)
                )
                neutralRelation
            } else {
                logger.error("Failed to remove relation for unenemy")
                null
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error accepting unenemy", e)
            return null
        }
    }
    
    override fun rejectRequest(relationId: UUID, rejectingGuildId: UUID, actorId: UUID): Boolean {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, rejectingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $rejectingGuildId")
                return false
            }
            
            val relation = relationRepository.getById(relationId)
            if (relation == null) {
                logger.warn("Relation $relationId not found")
                return false
            }
            
            // Validate the rejecting guild is part of the relation
            if (!relation.involves(rejectingGuildId)) {
                logger.warn("Guild $rejectingGuildId is not part of relation $relationId")
                return false
            }
            
            // Validate the relation is pending
            if (relation.status != RelationStatus.PENDING) {
                logger.warn("Relation $relationId is not pending")
                return false
            }
            
            // Update relation to rejected
            val rejectedRelation = relation.copy(
                status = RelationStatus.REJECTED,
                updatedAt = Instant.now()
            )
            
            return if (relationRepository.update(rejectedRelation)) {
                logger.info("Relation request $relationId rejected by guild $rejectingGuildId")
                true
            } else {
                logger.error("Failed to update rejected relation")
                false
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error rejecting request", e)
            return false
        }
    }
    
    override fun cancelRequest(relationId: UUID, cancellingGuildId: UUID, actorId: UUID): Boolean {
        try {
            // Validate permissions
            if (!canManageRelations(actorId, cancellingGuildId)) {
                logger.warn("Player $actorId does not have permission to manage relations for guild $cancellingGuildId")
                return false
            }
            
            val relation = relationRepository.getById(relationId)
            if (relation == null) {
                logger.warn("Relation $relationId not found")
                return false
            }
            
            // Validate the cancelling guild is part of the relation
            if (!relation.involves(cancellingGuildId)) {
                logger.warn("Guild $cancellingGuildId is not part of relation $relationId")
                return false
            }
            
            // Validate the relation is pending
            if (relation.status != RelationStatus.PENDING) {
                logger.warn("Relation $relationId is not pending")
                return false
            }
            
            // Remove the pending relation
            return if (relationRepository.remove(relationId)) {
                logger.info("Relation request $relationId cancelled by guild $cancellingGuildId")
                true
            } else {
                logger.error("Failed to remove cancelled relation")
                false
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error cancelling request", e)
            return false
        }
    }
    
    override fun getRelation(guildA: UUID, guildB: UUID): Relation? {
        return relationRepository.getByGuilds(guildA, guildB)
    }
    
    override fun getRelationType(guildA: UUID, guildB: UUID): RelationType {
        val relation = relationRepository.getByGuilds(guildA, guildB)
        return if (relation != null && relation.isActive()) {
            relation.type
        } else {
            RelationType.NEUTRAL
        }
    }
    
    override fun getGuildRelations(guildId: UUID): Set<Relation> {
        return relationRepository.getByGuild(guildId)
    }
    
    override fun getGuildRelationsByType(guildId: UUID, type: RelationType): Set<Relation> {
        return relationRepository.getByGuildAndType(guildId, type)
    }
    
    override fun getPendingRequests(guildId: UUID): Set<Relation> {
        return relationRepository.getByGuildAndStatus(guildId, RelationStatus.PENDING)
    }
    
    override fun getIncomingRequests(guildId: UUID): Set<Relation> {
        // For incoming requests, we need to filter based on which guild can accept
        return relationRepository.getByGuildAndStatus(guildId, RelationStatus.PENDING).filter { relation ->
            // The accepting guild depends on the relation type and which guild made the request
            when (relation.type) {
                RelationType.ALLY -> true // Both guilds can accept alliance requests
                RelationType.TRUCE, RelationType.NEUTRAL -> true // Both guilds can accept truce/unenemy requests
                else -> false
            }
        }.toSet()
    }
    
    override fun getOutgoingRequests(guildId: UUID): Set<Relation> {
        // For outgoing requests, these are requests the guild has made that are still pending
        return relationRepository.getByGuildAndStatus(guildId, RelationStatus.PENDING)
    }
    
    override fun canManageRelations(playerId: UUID, guildId: UUID): Boolean {
        try {
            val playerGuilds = memberService.getPlayerGuilds(playerId)
            if (!playerGuilds.contains(guildId)) {
                return false
            }
            
            // Check if player has MANAGE_RELATIONS permission in the guild
            return memberService.hasPermission(playerId, guildId, RankPermission.MANAGE_RELATIONS)
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error checking relation management permissions", e)
            return false
        }
    }
    
    override fun processExpiredRelations(): Int {
        try {
            val expiredRelations = relationRepository.getExpiredRelations()
            var processedCount = 0
            
            for (relation in expiredRelations) {
                when (relation.type) {
                    RelationType.TRUCE -> {
                        // When truce expires, revert to enemy status
                        val updatedRelation = relation.copy(
                            type = RelationType.ENEMY,
                            status = RelationStatus.ACTIVE,
                            expiresAt = null,
                            updatedAt = Instant.now()
                        )
                        
                        if (relationRepository.update(updatedRelation)) {
                            logger.info("Truce expired between guilds ${relation.guildA} and ${relation.guildB}, reverted to enemy")
                            // Fire relation change event
                            Bukkit.getPluginManager().callEvent(
                                GuildRelationChangeEvent(relation.guildA, relation.guildB, RelationType.ENEMY, updatedRelation)
                            )
                            processedCount++
                        }
                    }
                    else -> {
                        // Other relation types shouldn't expire, but mark as expired for safety
                        val expiredRelation = relation.copy(
                            status = RelationStatus.EXPIRED,
                            updatedAt = Instant.now()
                        )
                        
                        if (relationRepository.update(expiredRelation)) {
                            logger.info("Relation ${relation.id} marked as expired")
                            processedCount++
                        }
                    }
                }
            }
            
            return processedCount
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error processing expired relations", e)
            return 0
        }
    }
    
    override fun isValidRelationChange(fromGuildId: UUID, toGuildId: UUID, newType: RelationType): Boolean {
        // Basic validation
        if (fromGuildId == toGuildId) {
            return false
        }
        
        // Get current relation
        val currentRelation = relationRepository.getByGuilds(fromGuildId, toGuildId)
        val currentType = if (currentRelation != null && currentRelation.isActive()) {
            currentRelation.type
        } else {
            RelationType.NEUTRAL
        }
        
        // Validate state transitions
        return when (newType) {
            RelationType.ALLY -> currentType == RelationType.NEUTRAL || currentType == RelationType.TRUCE
            RelationType.ENEMY -> true // Can always declare war
            RelationType.TRUCE -> currentType == RelationType.ENEMY
            RelationType.NEUTRAL -> currentType == RelationType.ENEMY || currentType == RelationType.ALLY
        }
    }

    override fun isGuildInActiveWar(guildId: UUID): Boolean {
        val enemyRelations = relationRepository.getByGuildAndType(guildId, RelationType.ENEMY)
        return enemyRelations.any { relation ->
            relation.isActive() && relation.type == RelationType.ENEMY
        }
    }
}
