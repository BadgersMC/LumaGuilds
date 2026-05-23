package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
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
    private val memberService: MemberService,
    private val guildRepository: GuildRepository
) : RelationService {
    
    private val logger = LoggerFactory.getLogger(RelationServiceBukkit::class.java)
<<<<<<< HEAD
    
    /** Checks whether [guildId] is the recorded requester of [relation], allowing legacy null rows. */
    private fun isRequester(relation: Relation, guildId: UUID): Boolean =
        relation.requestingGuildId == guildId
    
=======

    companion object {
        /** Pending requests outstanding longer than this are auto-resolved by cleanupStaleRelations. */
        private val PENDING_REQUEST_TTL: Duration = Duration.ofDays(14)
    }

>>>>>>> pr-51
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
            
            // Check for existing relation. The relations table has a UNIQUE(guild_a, guild_b)
            // constraint, so at most one row exists per pair. A live pending request or an
            // active alliance must block a new request, but a *terminal* row (REJECTED/EXPIRED)
            // or a superseded one must be reused in place — otherwise the stale row permanently
            // blocks all future alliance requests (the "request that no longer exists still
            // blocks new ones" bug).
            val existingRelation = relationRepository.getByGuilds(requestingGuildId, targetGuildId)
            if (existingRelation != null) {
                if (existingRelation.status == RelationStatus.PENDING) {
                    logger.warn("A pending request already exists between guilds $requestingGuildId and $targetGuildId")
                    return null
                }
                if (existingRelation.isActive() && existingRelation.type == RelationType.ALLY) {
                    logger.warn("Guilds $requestingGuildId and $targetGuildId are already allied")
                    return null
                }
                // Reuse the stale/inactive row by updating it to a fresh pending alliance request.
                val reused = existingRelation.copy(
                    type = RelationType.ALLY,
                    status = RelationStatus.PENDING,
                    expiresAt = null,
                    requestingGuildId = requestingGuildId,
                    updatedAt = Instant.now()
                )
                return if (relationRepository.update(reused)) {
                    logger.info("Alliance request created (reused stale row) from guild $requestingGuildId to guild $targetGuildId")
                    reused
                } else {
                    logger.error("Failed to save alliance request (reusing stale row)")
                    null
                }
            }

            // Create pending alliance request
            val relation = Relation.create(
                guildA = requestingGuildId,
                guildB = targetGuildId,
                type = RelationType.ALLY,
                status = RelationStatus.PENDING,
<<<<<<< HEAD
                requestingGuildId = requestingGuildId,
=======
                requestingGuildId = requestingGuildId
>>>>>>> pr-51
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
            
            // Enforce direction: the requester cannot accept their own request
            // Legacy rows with null requestingGuildId can be accepted by either guild
            if (relation.requestingGuildId != null && isRequester(relation, acceptingGuildId)) {
                logger.warn("Guild $acceptingGuildId cannot accept its own alliance request")
                return null
            }
            
            // Update relation to active
            val updatedRelation = relation.copy(
                status = RelationStatus.ACTIVE,
                updatedAt = Instant.now()
            )
            
            return if (relationRepository.update(updatedRelation)) {
                logger.info("Alliance accepted between guilds ${relation.guildA} and ${relation.guildB}")
                addAllyInboundWhitelist(relation.guildA, relation.guildB)
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
            val wasAlly = existingRelation?.type == RelationType.ALLY && existingRelation.isActive()

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
                if (wasAlly) removeAllyInboundWhitelist(relation.guildA, relation.guildB)
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
            if (currentRelation == null) {
                logger.warn("Cannot request truce - no relation exists between guilds $requestingGuildId and $targetGuildId")
                return null
            }
            if (currentRelation.status == RelationStatus.PENDING) {
                logger.warn("Cannot request truce - a request is already pending between guilds $requestingGuildId and $targetGuildId")
                return null
            }
            // effectiveType treats the underlying war as still in force; a stale TRUCE/EXPIRED row
            // must not be mistaken for a non-war state.
            if (effectiveType(currentRelation) != RelationType.ENEMY) {
                logger.warn("Cannot request truce - guilds are not currently at war")
                return null
            }

            val expiresAt = Instant.now().plus(duration)

            // Update existing relation to pending truce
            val truceRelation = currentRelation.copy(
                type = RelationType.TRUCE,
                status = RelationStatus.PENDING,
                expiresAt = expiresAt,
                requestingGuildId = requestingGuildId,
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
            
            // Enforce direction: the requester cannot accept their own request
            // Legacy rows with null requestingGuildId can be accepted by either guild
            if (relation.requestingGuildId != null && isRequester(relation, acceptingGuildId)) {
                logger.warn("Guild $acceptingGuildId cannot accept its own truce request")
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
            if (currentRelation == null) {
                logger.warn("Cannot request unenemy - no relation exists between guilds $requestingGuildId and $targetGuildId")
                return null
            }
            if (currentRelation.status == RelationStatus.PENDING) {
                logger.warn("Cannot request unenemy - a request is already pending between guilds $requestingGuildId and $targetGuildId")
                return null
            }
            if (effectiveType(currentRelation) != RelationType.ENEMY) {
                logger.warn("Cannot request unenemy - guilds are not currently at war")
                return null
            }

            // Update existing relation to pending neutral request
            val unEnemyRelation = currentRelation.copy(
                type = RelationType.NEUTRAL,
                status = RelationStatus.PENDING,
                expiresAt = null,
                requestingGuildId = requestingGuildId,
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
            
            // Enforce direction: the requester cannot accept their own request
            // Legacy rows with null requestingGuildId can be accepted by either guild
            if (relation.requestingGuildId != null && isRequester(relation, acceptingGuildId)) {
                logger.warn("Guild $acceptingGuildId cannot accept its own unenemy request")
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
<<<<<<< HEAD
            
            // Enforce direction: only the non-requester can reject
            // Legacy rows with null requestingGuildId can be rejected by either guild
            if (relation.requestingGuildId != null && isRequester(relation, rejectingGuildId)) {
                logger.warn("Guild $rejectingGuildId cannot reject its own request")
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
=======

            // Rejecting a request must restore the pre-request state, NOT just mark it REJECTED:
            // a rejected truce/unenemy request leaves the guilds still at war (revert to ENEMY),
            // while a rejected alliance request returns to neutral (row removed). Leaving a
            // REJECTED row behind both lost the war state and blocked future requests.
            return resolvePendingRequest(relation, "rejected by guild $rejectingGuildId")
>>>>>>> pr-51
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
<<<<<<< HEAD
            
            // Enforce direction: only the requester can cancel
            // Legacy rows with null requestingGuildId can be cancelled by either guild
            if (relation.requestingGuildId != null && !isRequester(relation, cancellingGuildId)) {
                logger.warn("Guild $cancellingGuildId cannot cancel a request it did not send")
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
=======

            // Cancelling restores the pre-request state (same rules as rejection): a cancelled
            // truce/unenemy request must keep the guilds at war, not silently end it by deleting
            // the row.
            return resolvePendingRequest(relation, "cancelled by guild $cancellingGuildId")
>>>>>>> pr-51
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
        val relation = relationRepository.getByGuilds(guildA, guildB) ?: return RelationType.NEUTRAL
        return effectiveType(relation)
    }

    /**
     * Computes the effective relation type for a row, accounting for pending de-escalation
     * requests. A pending TRUCE or unenemy (NEUTRAL) request is always raised from an active
     * ENEMY state and does NOT take effect until accepted, so the guilds remain enemies until
     * then. Inactive/terminal rows (REJECTED/EXPIRED) read as NEUTRAL.
     */
    private fun effectiveType(relation: Relation): RelationType = when {
        relation.isActive() -> relation.type
        relation.status == RelationStatus.PENDING &&
            (relation.type == RelationType.TRUCE || relation.type == RelationType.NEUTRAL) ->
            RelationType.ENEMY
        else -> RelationType.NEUTRAL
    }

    /**
     * Restores the pre-request state when a pending request is rejected, cancelled, or expires.
     * - Pending TRUCE / unenemy (NEUTRAL): the underlying war stands — revert to active ENEMY.
     * - Pending ALLY: return to neutral by removing the row.
     * Returns true on success. [reason] is used only for logging.
     */
    private fun resolvePendingRequest(relation: Relation, reason: String): Boolean {
        return when (relation.type) {
            RelationType.TRUCE, RelationType.NEUTRAL -> {
                val reverted = relation.copy(
                    type = RelationType.ENEMY,
                    status = RelationStatus.ACTIVE,
                    expiresAt = null,
                    requestingGuildId = null,
                    updatedAt = Instant.now()
                )
                if (relationRepository.update(reverted)) {
                    logger.info("Pending request ${relation.id} $reason - reverted to enemy (war stands)")
                    true
                } else {
                    logger.error("Failed to revert pending request ${relation.id}")
                    false
                }
            }
            RelationType.ALLY, RelationType.ENEMY -> {
                if (relationRepository.remove(relation.id)) {
                    logger.info("Pending request ${relation.id} $reason - removed (back to neutral)")
                    true
                } else {
                    logger.error("Failed to remove pending request ${relation.id}")
                    false
                }
            }
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
        // Incoming = pending requests this guild did NOT initiate (the other guild is the requester).
        // Legacy rows with no recorded requester (null) are surfaced here so they remain actionable.
        return relationRepository.getByGuildAndStatus(guildId, RelationStatus.PENDING).filter { relation ->
            relation.requestingGuildId != guildId
        }.toSet()
    }

    override fun getOutgoingRequests(guildId: UUID): Set<Relation> {
        // Outgoing = pending requests this guild initiated.
        return relationRepository.getByGuildAndStatus(guildId, RelationStatus.PENDING).filter { relation ->
            relation.requestingGuildId == guildId
        }.toSet()
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

    override fun cleanupStaleRelations(): Int {
        var cleaned = 0
        try {
            // 1) Remove terminal rows (REJECTED/EXPIRED). With the current request flows these are
            //    no longer produced, but legacy databases may still contain them — and a lingering
            //    terminal row occupies the UNIQUE(guild_a, guild_b) slot and blocks new requests.
            val terminal = relationRepository.getByStatus(RelationStatus.REJECTED) +
                relationRepository.getByStatus(RelationStatus.EXPIRED)
            for (relation in terminal) {
                if (relationRepository.remove(relation.id)) {
                    logger.info("Cleaned up terminal (${relation.status}) relation ${relation.id} between ${relation.guildA} and ${relation.guildB}")
                    cleaned++
                }
            }

            // 2) Auto-resolve pending requests that have been outstanding too long.
            val cutoff = Instant.now().minus(PENDING_REQUEST_TTL)
            val stalePending = relationRepository.getByStatus(RelationStatus.PENDING)
                .filter { it.updatedAt.isBefore(cutoff) }
            for (relation in stalePending) {
                if (resolvePendingRequest(relation, "auto-expired after $PENDING_REQUEST_TTL")) {
                    cleaned++
                }
            }
        } catch (e: Exception) {
            logger.error("Error cleaning up stale relations", e)
        }
        return cleaned
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
        // A guild is at war when it has an active ENEMY relation, OR a pending de-escalation
        // (truce/unenemy) request that has not yet been accepted — those leave the war in force.
        return relationRepository.getByGuild(guildId).any { relation ->
            effectiveType(relation) == RelationType.ENEMY
        }
    }

    // Whitelist drift note: canUseAllyHome additionally re-checks the active ALLY relation
    // at teleport time, so a stale whitelist entry left behind by a failed write below cannot
    // grant access without a currently active alliance. Failures are logged for operator
    // visibility — a manager can re-grant via AllyHomeAccessMenu if needed.
    private fun addAllyInboundWhitelist(guildA: UUID, guildB: UUID) {
        val gA = guildRepository.getById(guildA)
        val gB = guildRepository.getById(guildB)
        if (gA != null && !guildRepository.update(gA.copy(allyHomeAllowedGuilds = gA.allyHomeAllowedGuilds + guildB))) {
            logger.warn("Failed to add $guildB to $guildA.allyHomeAllowedGuilds after alliance — manager may need to re-grant via AllyHomeAccessMenu")
        }
        if (gB != null && !guildRepository.update(gB.copy(allyHomeAllowedGuilds = gB.allyHomeAllowedGuilds + guildA))) {
            logger.warn("Failed to add $guildA to $guildB.allyHomeAllowedGuilds after alliance — manager may need to re-grant via AllyHomeAccessMenu")
        }
    }

    private fun removeAllyInboundWhitelist(guildA: UUID, guildB: UUID) {
        val gA = guildRepository.getById(guildA)
        val gB = guildRepository.getById(guildB)
        if (gA != null && !guildRepository.update(gA.copy(allyHomeAllowedGuilds = gA.allyHomeAllowedGuilds - guildB))) {
            logger.warn("Failed to remove $guildB from $guildA.allyHomeAllowedGuilds after alliance break — stale entry left behind (canUseAllyHome's active-ally check still gates access)")
        }
        if (gB != null && !guildRepository.update(gB.copy(allyHomeAllowedGuilds = gB.allyHomeAllowedGuilds - guildA))) {
            logger.warn("Failed to remove $guildA from $guildB.allyHomeAllowedGuilds after alliance break — stale entry left behind (canUseAllyHome's active-ally check still gates access)")
        }
    }
}
