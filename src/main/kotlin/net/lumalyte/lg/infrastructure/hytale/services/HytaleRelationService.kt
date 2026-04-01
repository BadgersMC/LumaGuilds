package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RankPermission
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Hytale implementation of RelationService.
 *
 * Handles guild diplomatic relations including alliances, wars, and truces.
 */
class HytaleRelationService(
    private val relationRepository: RelationRepository,
    private val rankService: RankService
) : RelationService {

    override fun requestAlliance(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation? {
        // Check permission
        if (!canManageRelations(actorId, requestingGuildId)) {
            return null
        }

        // Check if relation already exists
        if (getRelation(requestingGuildId, targetGuildId) != null) {
            return null
        }

        // Create pending alliance
        val relation = Relation.create(
            guildA = requestingGuildId,
            guildB = targetGuildId,
            type = RelationType.ALLY,
            status = RelationStatus.PENDING
        )

        return if (relationRepository.add(relation)) relation else null
    }

    override fun acceptAlliance(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation? {
        // Check permission
        if (!canManageRelations(actorId, acceptingGuildId)) {
            return null
        }

        val relation = relationRepository.getById(relationId) ?: return null

        // Verify this guild is part of the relation
        if (!relation.involves(acceptingGuildId)) {
            return null
        }

        // Verify it's a pending alliance
        if (relation.type != RelationType.ALLY || relation.status != RelationStatus.PENDING) {
            return null
        }

        // Activate the relation
        val updated = relation.copy(status = RelationStatus.ACTIVE, updatedAt = Instant.now())
        return if (relationRepository.update(updated)) updated else null
    }

    override fun declareWar(declaringGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation? {
        // Check permission
        if (!rankService.hasPermission(actorId, declaringGuildId, RankPermission.DECLARE_WAR)) {
            return null
        }

        // Get existing relation
        val existing = getRelation(declaringGuildId, targetGuildId)

        // Can't declare war on allies
        if (existing?.type == RelationType.ALLY && existing.status == RelationStatus.ACTIVE) {
            return null
        }

        // If already enemies, return existing relation
        if (existing?.type == RelationType.ENEMY && existing.status == RelationStatus.ACTIVE) {
            return existing
        }

        // Create or update to enemy relation
        val relation = Relation.create(
            guildA = declaringGuildId,
            guildB = targetGuildId,
            type = RelationType.ENEMY,
            status = RelationStatus.ACTIVE
        )

        return if (existing != null) {
            val updated = relation.copy(id = existing.id, createdAt = existing.createdAt)
            if (relationRepository.update(updated)) updated else null
        } else {
            if (relationRepository.add(relation)) relation else null
        }
    }

    override fun requestTruce(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID, duration: Duration): Relation? {
        // Check permission
        if (!canManageRelations(actorId, requestingGuildId)) {
            return null
        }

        // Must be enemies to request truce
        val existing = getRelation(requestingGuildId, targetGuildId)
        if (existing?.type != RelationType.ENEMY || existing.status != RelationStatus.ACTIVE) {
            return null
        }

        // Create pending truce
        val expiresAt = Instant.now().plus(duration)
        val relation = Relation.create(
            guildA = requestingGuildId,
            guildB = targetGuildId,
            type = RelationType.TRUCE,
            status = RelationStatus.PENDING,
            expiresAt = expiresAt
        )

        // Update existing relation
        val updated = relation.copy(id = existing.id, createdAt = existing.createdAt)
        return if (relationRepository.update(updated)) updated else null
    }

    override fun acceptTruce(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation? {
        // Check permission
        if (!canManageRelations(actorId, acceptingGuildId)) {
            return null
        }

        val relation = relationRepository.getById(relationId) ?: return null

        // Verify this guild is part of the relation
        if (!relation.involves(acceptingGuildId)) {
            return null
        }

        // Verify it's a pending truce
        if (relation.type != RelationType.TRUCE || relation.status != RelationStatus.PENDING) {
            return null
        }

        // Activate the truce
        val updated = relation.copy(status = RelationStatus.ACTIVE, updatedAt = Instant.now())
        return if (relationRepository.update(updated)) updated else null
    }

    override fun requestUnenemy(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation? {
        // Check permission
        if (!canManageRelations(actorId, requestingGuildId)) {
            return null
        }

        // Must be enemies or in truce to request unenemy
        val existing = getRelation(requestingGuildId, targetGuildId) ?: return null
        if (existing.type != RelationType.ENEMY && existing.type != RelationType.TRUCE) {
            return null
        }

        // Create pending neutral relation
        val relation = Relation.create(
            guildA = requestingGuildId,
            guildB = targetGuildId,
            type = RelationType.NEUTRAL,
            status = RelationStatus.PENDING
        )

        // Update existing relation
        val updated = relation.copy(id = existing.id, createdAt = existing.createdAt)
        return if (relationRepository.update(updated)) updated else null
    }

    override fun acceptUnenemy(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation? {
        // Check permission
        if (!canManageRelations(actorId, acceptingGuildId)) {
            return null
        }

        val relation = relationRepository.getById(relationId) ?: return null

        // Verify this guild is part of the relation
        if (!relation.involves(acceptingGuildId)) {
            return null
        }

        // Verify it's a pending neutral request
        if (relation.type != RelationType.NEUTRAL || relation.status != RelationStatus.PENDING) {
            return null
        }

        // Delete the relation (return to true neutral)
        return if (relationRepository.remove(relationId)) relation else null
    }

    override fun rejectRequest(relationId: UUID, rejectingGuildId: UUID, actorId: UUID): Boolean {
        // Check permission
        if (!canManageRelations(actorId, rejectingGuildId)) {
            return false
        }

        val relation = relationRepository.getById(relationId) ?: return false

        // Verify this guild is part of the relation
        if (!relation.involves(rejectingGuildId)) {
            return false
        }

        // Verify it's pending
        if (relation.status != RelationStatus.PENDING) {
            return false
        }

        // Mark as rejected
        val updated = relation.copy(status = RelationStatus.REJECTED, updatedAt = Instant.now())
        return relationRepository.update(updated)
    }

    override fun cancelRequest(relationId: UUID, cancellingGuildId: UUID, actorId: UUID): Boolean {
        // Check permission
        if (!canManageRelations(actorId, cancellingGuildId)) {
            return false
        }

        val relation = relationRepository.getById(relationId) ?: return false

        // Verify this guild is part of the relation
        if (!relation.involves(cancellingGuildId)) {
            return false
        }

        // Verify it's pending
        if (relation.status != RelationStatus.PENDING) {
            return false
        }

        // Delete the pending request
        return relationRepository.remove(relationId)
    }

    override fun getRelation(guildA: UUID, guildB: UUID): Relation? {
        return relationRepository.getByGuilds(guildA, guildB)
    }

    override fun getRelationType(guildA: UUID, guildB: UUID): RelationType {
        val relation = getRelation(guildA, guildB)
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
        // Incoming requests are ones where this guild is NOT the first guild listed
        // (since the first guild is typically the requester)
        return getPendingRequests(guildId).filter { relation ->
            relation.guildB == guildId
        }.toSet()
    }

    override fun getOutgoingRequests(guildId: UUID): Set<Relation> {
        // Outgoing requests are ones where this guild IS the first guild listed
        return getPendingRequests(guildId).filter { relation ->
            relation.guildA == guildId
        }.toSet()
    }

    override fun canManageRelations(playerId: UUID, guildId: UUID): Boolean {
        return rankService.hasPermission(playerId, guildId, RankPermission.MANAGE_RELATIONS)
    }

    override fun processExpiredRelations(): Int {
        val allRelations = relationRepository.getAll()
        var count = 0

        allRelations.forEach { relation ->
            if (relation.type == RelationType.TRUCE && !relation.isActive()) {
                // Truce expired, revert to enemy
                val updated = relation.copy(
                    type = RelationType.ENEMY,
                    status = RelationStatus.ACTIVE,
                    expiresAt = null,
                    updatedAt = Instant.now()
                )
                if (relationRepository.update(updated)) {
                    count++
                }
            }
        }

        return count
    }

    override fun isValidRelationChange(fromGuildId: UUID, toGuildId: UUID, newType: RelationType): Boolean {
        val currentRelation = getRelation(fromGuildId, toGuildId)
        val currentType = currentRelation?.type ?: RelationType.NEUTRAL

        // Cannot change to same type
        if (currentType == newType) {
            return false
        }

        // Allies cannot directly become enemies (must break alliance first)
        if (currentType == RelationType.ALLY && currentRelation?.status == RelationStatus.ACTIVE && newType == RelationType.ENEMY) {
            return false
        }

        return true
    }

    override fun isGuildInActiveWar(guildId: UUID): Boolean {
        val enemyRelations = getGuildRelationsByType(guildId, RelationType.ENEMY)
        return enemyRelations.any { it.status == RelationStatus.ACTIVE }
    }
}
