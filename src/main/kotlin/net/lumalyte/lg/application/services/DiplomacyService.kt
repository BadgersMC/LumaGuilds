package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.DiplomaticHistoryRepository
import net.lumalyte.lg.application.persistence.DiplomaticRelationRepository
import net.lumalyte.lg.application.persistence.DiplomaticRequestRepository
import net.lumalyte.lg.domain.entities.DiplomaticHistory
import net.lumalyte.lg.domain.entities.DiplomaticRelation
import net.lumalyte.lg.domain.entities.DiplomaticRelationType
import net.lumalyte.lg.domain.entities.DiplomaticRequest
import net.lumalyte.lg.domain.entities.DiplomaticRequestType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

interface DiplomacyService {
    fun breakAlliance(guildId: UUID, targetGuildId: UUID): Boolean
    fun breakRelation(guildId: UUID, targetGuildId: UUID): Boolean
    fun getRelations(guildId: UUID): List<DiplomaticRelation>
    fun createRelation(guildId: UUID, targetGuildId: UUID, type: DiplomaticRelationType): Boolean
    fun updateRelation(relationId: UUID, updates: Map<String, Any>): Boolean
    fun logDiplomaticEvent(guildId: UUID, targetGuildId: UUID, eventType: String, description: String): Boolean

    // Diplomatic Request Management
    fun sendRequest(fromGuildId: UUID, toGuildId: UUID, type: DiplomaticRequestType, message: String? = null): DiplomaticRequest?
    fun acceptRequest(requestId: UUID, acceptedBy: UUID): Boolean
    fun rejectRequest(requestId: UUID, rejectedBy: UUID): Boolean
    fun getPendingRequests(guildId: UUID): List<DiplomaticRequest>
    fun getIncomingRequests(guildId: UUID): List<DiplomaticRequest>
    fun getOutgoingRequests(guildId: UUID): List<DiplomaticRequest>

    // Diplomatic History Management
    fun getDiplomaticHistory(guildId: UUID): List<DiplomaticHistory>
    fun getDiplomaticHistoryByType(guildId: UUID, eventType: String): List<DiplomaticHistory>
    fun getDiplomaticHistoryBetweenGuilds(guildA: UUID, guildB: UUID): List<DiplomaticHistory>
    fun getRecentDiplomaticHistory(guildId: UUID, limit: Int = 50): List<DiplomaticHistory>
}

class DiplomacyServiceImpl(
    private val diplomaticRelationRepository: DiplomaticRelationRepository,
    private val diplomaticRequestRepository: DiplomaticRequestRepository,
    private val diplomaticHistoryRepository: DiplomaticHistoryRepository
) : DiplomacyService {

    private val logger = LoggerFactory.getLogger(DiplomacyServiceImpl::class.java)

    override fun breakAlliance(guildId: UUID, targetGuildId: UUID): Boolean {
        try {
            val relation = diplomaticRelationRepository.getByGuilds(guildId, targetGuildId)
            if (relation != null) {
                return diplomaticRelationRepository.remove(relation.id)
            } else {
                logger.warn("No alliance relation found between $guildId and $targetGuildId")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error breaking alliance between $guildId and $targetGuildId", e)
            return false
        }
    }

    override fun breakRelation(guildId: UUID, targetGuildId: UUID): Boolean {
        try {
            val relation = diplomaticRelationRepository.getByGuilds(guildId, targetGuildId)
            if (relation != null) {
                return diplomaticRelationRepository.remove(relation.id)
            } else {
                logger.warn("No relation found between $guildId and $targetGuildId")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error breaking relation between $guildId and $targetGuildId", e)
            return false
        }
    }

    override fun getRelations(guildId: UUID): List<DiplomaticRelation> {
        try {
            return diplomaticRelationRepository.getActiveRelations(guildId)
        } catch (e: Exception) {
            logger.error("Error getting relations for guild $guildId", e)
            return emptyList()
        }
    }

    override fun createRelation(guildId: UUID, targetGuildId: UUID, type: DiplomaticRelationType): Boolean {
        try {
            // Check if relation already exists
            val existingRelation = diplomaticRelationRepository.getByGuilds(guildId, targetGuildId)
            if (existingRelation != null) {
                logger.warn("Relation already exists between $guildId and $targetGuildId")
                return false
            }

            val relation = DiplomaticRelation(
                id = UUID.randomUUID(),
                guildId = guildId,
                targetGuildId = targetGuildId,
                type = type,
                establishedAt = java.time.Instant.now()
            )

            return diplomaticRelationRepository.add(relation)
        } catch (e: Exception) {
            logger.error("Error creating relation between $guildId and $targetGuildId", e)
            return false
        }
    }

    override fun updateRelation(relationId: UUID, updates: Map<String, Any>): Boolean {
        try {
            val existingRelation = diplomaticRelationRepository.getById(relationId)
            if (existingRelation == null) {
                logger.warn("Relation $relationId not found for update")
                return false
            }

            // Create updated relation with new values
            val updatedRelation = existingRelation.copy(
                expiresAt = updates["expires_at"] as? java.time.Instant ?: existingRelation.expiresAt
            )

            return diplomaticRelationRepository.update(updatedRelation)
        } catch (e: Exception) {
            logger.error("Error updating relation $relationId", e)
            return false
        }
    }

    override fun logDiplomaticEvent(guildId: UUID, targetGuildId: UUID, eventType: String, description: String): Boolean {
        try {
            // For now, just log to console. In the future, this could be stored in a diplomatic history table
            logger.info("Diplomatic Event: $eventType - $description for guild $guildId with target $targetGuildId")
            return true
        } catch (e: Exception) {
            logger.error("Error logging diplomatic event", e)
            return false
        }
    }

    override fun sendRequest(fromGuildId: UUID, toGuildId: UUID, type: DiplomaticRequestType, message: String?): DiplomaticRequest? {
        try {
            // Check if there's already a pending request of the same type
            val existingRequest = diplomaticRequestRepository.getBetweenGuilds(fromGuildId, toGuildId)
                .find { it.type == type && it.isActive() }

            if (existingRequest != null) {
                logger.warn("Request of type $type already exists between $fromGuildId and $toGuildId")
                return null
            }

            // Check if there's already a relation of this type
            if (diplomaticRelationRepository.hasRelationType(fromGuildId, toGuildId, convertRequestTypeToRelationType(type))) {
                logger.warn("Relation of type ${type.name} already exists between $fromGuildId and $toGuildId")
                return null
            }

            val request = DiplomaticRequest(
                id = UUID.randomUUID(),
                fromGuildId = fromGuildId,
                toGuildId = toGuildId,
                type = type,
                message = message,
                requestedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(604800) // Expires in 7 days
            )

            return if (diplomaticRequestRepository.add(request)) {
                logger.info("Diplomatic request sent: ${type.name} from $fromGuildId to $toGuildId")
                request
            } else {
                logger.error("Failed to save diplomatic request to database")
                null
            }
        } catch (e: Exception) {
            logger.error("Error sending diplomatic request from $fromGuildId to $toGuildId", e)
            return null
        }
    }

    override fun acceptRequest(requestId: UUID, acceptedBy: UUID): Boolean {
        try {
            val request = diplomaticRequestRepository.getById(requestId)
            if (request == null || !request.isActive()) {
                logger.warn("Request $requestId not found or expired")
                return false
            }

            // Create the diplomatic relation
            val relationType = convertRequestTypeToRelationType(request.type)
            val success = createRelation(request.toGuildId, request.fromGuildId, relationType)

            if (success) {
                // Remove the request
                diplomaticRequestRepository.remove(requestId)

                // Log the acceptance
                logDiplomaticEvent(request.toGuildId, request.fromGuildId, "${request.type.name.lowercase()}_accepted", "Diplomatic request accepted")

                logger.info("Diplomatic request $requestId accepted by $acceptedBy")
                return true
            } else {
                logger.error("Failed to create diplomatic relation for accepted request $requestId")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error accepting diplomatic request $requestId", e)
            return false
        }
    }

    override fun rejectRequest(requestId: UUID, rejectedBy: UUID): Boolean {
        try {
            val request = diplomaticRequestRepository.getById(requestId)
            if (request == null) {
                logger.warn("Request $requestId not found")
                return false
            }

            // Remove the request
            val success = diplomaticRequestRepository.remove(requestId)

            if (success) {
                // Log the rejection
                logDiplomaticEvent(request.toGuildId, request.fromGuildId, "${request.type.name.lowercase()}_rejected", "Diplomatic request rejected")

                logger.info("Diplomatic request $requestId rejected by $rejectedBy")
            }

            return success
        } catch (e: Exception) {
            logger.error("Error rejecting diplomatic request $requestId", e)
            return false
        }
    }

    override fun getPendingRequests(guildId: UUID): List<DiplomaticRequest> {
        try {
            return diplomaticRequestRepository.getActiveRequests(guildId)
        } catch (e: Exception) {
            logger.error("Error getting pending requests for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getIncomingRequests(guildId: UUID): List<DiplomaticRequest> {
        try {
            return diplomaticRequestRepository.getIncomingRequests(guildId)
        } catch (e: Exception) {
            logger.error("Error getting incoming requests for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getOutgoingRequests(guildId: UUID): List<DiplomaticRequest> {
        try {
            return diplomaticRequestRepository.getOutgoingRequests(guildId)
        } catch (e: Exception) {
            logger.error("Error getting outgoing requests for guild $guildId", e)
            return emptyList()
        }
    }

    private fun convertRequestTypeToRelationType(requestType: DiplomaticRequestType): DiplomaticRelationType {
        return when (requestType) {
            DiplomaticRequestType.ALLIANCE_REQUEST -> DiplomaticRelationType.ALLIANCE
            DiplomaticRequestType.TRUCE_REQUEST -> DiplomaticRelationType.TRUCE
            DiplomaticRequestType.WAR_DECLARATION -> DiplomaticRelationType.ENEMY
        }
    }

    // Diplomatic History methods
    override fun getDiplomaticHistory(guildId: UUID): List<net.lumalyte.lg.domain.entities.DiplomaticHistory> {
        try {
            return diplomaticHistoryRepository.getByGuild(guildId)
        } catch (e: Exception) {
            logger.error("Error getting diplomatic history for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getDiplomaticHistoryByType(guildId: UUID, eventType: String): List<net.lumalyte.lg.domain.entities.DiplomaticHistory> {
        try {
            return diplomaticHistoryRepository.getByGuildAndType(guildId, eventType)
        } catch (e: Exception) {
            logger.error("Error getting diplomatic history by type for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getDiplomaticHistoryBetweenGuilds(guildA: UUID, guildB: UUID): List<net.lumalyte.lg.domain.entities.DiplomaticHistory> {
        try {
            return diplomaticHistoryRepository.getBetweenGuilds(guildA, guildB)
        } catch (e: Exception) {
            logger.error("Error getting diplomatic history between guilds $guildA and $guildB", e)
            return emptyList()
        }
    }

    override fun getRecentDiplomaticHistory(guildId: UUID, limit: Int): List<net.lumalyte.lg.domain.entities.DiplomaticHistory> {
        try {
            return diplomaticHistoryRepository.getRecentByGuild(guildId, limit)
        } catch (e: Exception) {
            logger.error("Error getting recent diplomatic history for guild $guildId", e)
            return emptyList()
        }
    }
}
