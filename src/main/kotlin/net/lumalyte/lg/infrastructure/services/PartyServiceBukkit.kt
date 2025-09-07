package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.*
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PartyServiceBukkit(
    private val partyRepository: PartyRepository,
    private val partyRequestRepository: PartyRequestRepository,
    private val memberService: MemberService
) : PartyService {
    
    private val logger = LoggerFactory.getLogger(PartyServiceBukkit::class.java)

    override fun createParty(party: Party): Party? {
        try {
            // Validate that the party has at least 1 guild
            if (party.guildIds.isEmpty()) {
                logger.warn("Cannot create party with no guilds")
                return null
            }

            // For private parties, only 1 guild is required
            val isPrivateParty = party.guildIds.size == 1

            // Validate that the leader has permission to manage parties for their guild
            val leaderGuildId = memberService.getPlayerGuilds(party.leaderId).firstOrNull()
            if (leaderGuildId == null || !canManageParties(party.leaderId, leaderGuildId)) {
                logger.warn("Player ${party.leaderId} does not have permission to create parties")
                return null
            }

            // Check if any of the guilds are already in an active party
            // For private parties, allow multiple parties per guild (one per player/leader)
            if (!isPrivateParty) {
                for (guildId in party.guildIds) {
                    val existingParties = partyRepository.getActivePartiesByGuild(guildId)
                    if (existingParties.isNotEmpty()) {
                        logger.warn("Guild $guildId is already in an active party")
                        return null
                    }
                }
            }

            // Create the party
            return if (partyRepository.add(party)) {
                logger.info("Party ${party.id} created with ${party.guildIds.size} guilds by player ${party.leaderId}")
                party
            } else {
                logger.error("Failed to create party ${party.id}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error creating party", e)
            return null
        }
    }

    override fun sendPartyRequest(fromGuildId: UUID, toGuildId: UUID, requesterId: UUID, message: String?): PartyRequest? {
        try {
            // Validate requester has permission
            if (!canManageParties(requesterId, fromGuildId)) {
                logger.warn("Player $requesterId does not have permission to send party requests for guild $fromGuildId")
                return null
            }
            
            // Check if request already exists
            val existingRequest = partyRequestRepository.findActiveRequestBetweenGuilds(fromGuildId, toGuildId)
            if (existingRequest != null) {
                logger.warn("Active party request already exists between guilds $fromGuildId and $toGuildId")
                return null
            }
            
            // Check if guilds are already in a party together
            val existingParty = partyRepository.findPartyByGuilds(setOf(fromGuildId, toGuildId))
            if (existingParty != null) {
                logger.warn("Guilds $fromGuildId and $toGuildId are already in a party together")
                return null
            }
            
            // Create party request
            val request = PartyRequest.create(fromGuildId, toGuildId, requesterId, message)
            
            return if (partyRequestRepository.add(request)) {
                logger.info("Party request sent from guild $fromGuildId to guild $toGuildId by player $requesterId")
                request
            } else {
                logger.error("Failed to save party request")
                null
            }
        } catch (e: Exception) {
            logger.error("Error sending party request", e)
            return null
        }
    }
    
    override fun acceptPartyRequest(requestId: UUID, acceptingGuildId: UUID, accepterId: UUID): Party? {
        try {
            // Validate accepter has permission
            if (!canManageParties(accepterId, acceptingGuildId)) {
                logger.warn("Player $accepterId does not have permission to accept party requests for guild $acceptingGuildId")
                return null
            }
            
            val request = partyRequestRepository.getById(requestId)
            if (request == null) {
                logger.warn("Party request $requestId not found")
                return null
            }
            
            // Validate request is for this guild and is valid
            if (request.toGuildId != acceptingGuildId) {
                logger.warn("Guild $acceptingGuildId cannot accept request $requestId (not addressed to them)")
                return null
            }
            
            if (!request.isValid()) {
                logger.warn("Party request $requestId is no longer valid")
                return null
            }
            
            // Check if there's an existing party involving the requesting guild
            val existingParties = partyRepository.getActivePartiesByGuild(request.fromGuildId)
            val existingParty = existingParties.firstOrNull()
            
            val party = if (existingParty != null) {
                // Add accepting guild to existing party
                val updatedGuildIds = existingParty.guildIds + acceptingGuildId
                existingParty.copy(guildIds = updatedGuildIds)
            } else {
                // Create new party
                Party(
                    id = UUID.randomUUID(),
                    guildIds = setOf(request.fromGuildId, acceptingGuildId),
                    leaderId = request.requesterId,
                    createdAt = Instant.now()
                )
            }
            
            // Update request status
            val acceptedRequest = request.copy(status = PartyRequestStatus.ACCEPTED)
            partyRequestRepository.update(acceptedRequest)
            
            // Save or update party
            val success = if (existingParty != null) {
                partyRepository.update(party)
            } else {
                partyRepository.add(party)
            }
            
            return if (success) {
                logger.info("Party request $requestId accepted by guild $acceptingGuildId")
                party
            } else {
                logger.error("Failed to save party")
                null
            }
        } catch (e: Exception) {
            logger.error("Error accepting party request", e)
            return null
        }
    }
    
    override fun rejectPartyRequest(requestId: UUID, rejectingGuildId: UUID, rejecterId: UUID): Boolean {
        try {
            // Validate rejecter has permission
            if (!canManageParties(rejecterId, rejectingGuildId)) {
                logger.warn("Player $rejecterId does not have permission to reject party requests for guild $rejectingGuildId")
                return false
            }
            
            val request = partyRequestRepository.getById(requestId)
            if (request == null) {
                logger.warn("Party request $requestId not found")
                return false
            }
            
            // Validate request is for this guild
            if (request.toGuildId != rejectingGuildId) {
                logger.warn("Guild $rejectingGuildId cannot reject request $requestId (not addressed to them)")
                return false
            }
            
            // Update request status
            val rejectedRequest = request.copy(status = PartyRequestStatus.REJECTED)
            val success = partyRequestRepository.update(rejectedRequest)
            
            if (success) {
                logger.info("Party request $requestId rejected by guild $rejectingGuildId")
            }
            
            return success
        } catch (e: Exception) {
            logger.error("Error rejecting party request", e)
            return false
        }
    }
    
    override fun cancelPartyRequest(requestId: UUID, cancellingGuildId: UUID, cancellerId: UUID): Boolean {
        try {
            // Validate canceller has permission
            if (!canManageParties(cancellerId, cancellingGuildId)) {
                logger.warn("Player $cancellerId does not have permission to cancel party requests for guild $cancellingGuildId")
                return false
            }
            
            val request = partyRequestRepository.getById(requestId)
            if (request == null) {
                logger.warn("Party request $requestId not found")
                return false
            }
            
            // Validate request is from this guild
            if (request.fromGuildId != cancellingGuildId) {
                logger.warn("Guild $cancellingGuildId cannot cancel request $requestId (not their request)")
                return false
            }
            
            // Remove the request
            val success = partyRequestRepository.remove(requestId)
            
            if (success) {
                logger.info("Party request $requestId cancelled by guild $cancellingGuildId")
            }
            
            return success
        } catch (e: Exception) {
            logger.error("Error cancelling party request", e)
            return false
        }
    }
    
    override fun inviteToParty(partyId: UUID, inviterGuildId: UUID, targetGuildId: UUID, inviterId: UUID): PartyRequest? {
        try {
            // Validate inviter has permission
            if (!canManageParties(inviterId, inviterGuildId)) {
                logger.warn("Player $inviterId does not have permission to invite to parties for guild $inviterGuildId")
                return null
            }
            
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                logger.warn("Party $partyId not found or not active")
                return null
            }
            
            // Validate inviter's guild is in the party
            if (!party.includesGuild(inviterGuildId)) {
                logger.warn("Guild $inviterGuildId is not part of party $partyId")
                return null
            }
            
            // Check if target guild is already in the party
            if (party.includesGuild(targetGuildId)) {
                logger.warn("Guild $targetGuildId is already part of party $partyId")
                return null
            }
            
            // Send party request
            return sendPartyRequest(inviterGuildId, targetGuildId, inviterId, "Invitation to join party")
        } catch (e: Exception) {
            logger.error("Error inviting guild to party", e)
            return null
        }
    }
    
    override fun leaveParty(partyId: UUID, guildId: UUID, actorId: UUID): Party? {
        try {
            // Validate actor has permission
            if (!canManageParties(actorId, guildId)) {
                logger.warn("Player $actorId does not have permission to leave parties for guild $guildId")
                return null
            }
            
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                logger.warn("Party $partyId not found or not active")
                return null
            }
            
            // Validate guild is in the party
            if (!party.includesGuild(guildId)) {
                logger.warn("Guild $guildId is not part of party $partyId")
                return null
            }
            
            val remainingGuilds = party.guildIds - guildId
            
            return if (remainingGuilds.size < 2) {
                // Dissolve party if less than 2 guilds remain
                val dissolvedParty = party.copy(status = PartyStatus.DISSOLVED)
                partyRepository.update(dissolvedParty)
                logger.info("Party $partyId dissolved as guild $guildId left (insufficient guilds remaining)")
                null
            } else {
                // Update party with remaining guilds
                val updatedParty = party.copy(guildIds = remainingGuilds)
                val success = partyRepository.update(updatedParty)
                
                if (success) {
                    logger.info("Guild $guildId left party $partyId")
                    updatedParty
                } else {
                    logger.error("Failed to update party after guild left")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error leaving party", e)
            return null
        }
    }
    
    override fun dissolveParty(partyId: UUID, dissolverId: UUID): Boolean {
        try {
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                logger.warn("Party $partyId not found or not active")
                return false
            }
            
            // Check if dissolver is the party leader or has permission in any participating guild
            var hasPermission = false
            if (party.leaderId == dissolverId) {
                hasPermission = true
            } else {
                // Check if player has party management permission in any participating guild
                for (guildId in party.guildIds) {
                    if (canManageParties(dissolverId, guildId)) {
                        hasPermission = true
                        break
                    }
                }
            }
            
            if (!hasPermission) {
                logger.warn("Player $dissolverId does not have permission to dissolve party $partyId")
                return false
            }
            
            // Dissolve party
            val dissolvedParty = party.copy(status = PartyStatus.DISSOLVED)
            val success = partyRepository.update(dissolvedParty)
            
            if (success) {
                logger.info("Party $partyId dissolved by player $dissolverId")
            }
            
            return success
        } catch (e: Exception) {
            logger.error("Error dissolving party", e)
            return false
        }
    }
    
    override fun getActivePartiesForGuild(guildId: UUID): Set<Party> {
        return partyRepository.getActivePartiesByGuild(guildId)
    }
    
    override fun getActivePartyForPlayer(playerId: UUID): Party? {
        val guilds = memberService.getPlayerGuilds(playerId)
        
        for (guildId in guilds) {
            val parties = partyRepository.getActivePartiesByGuild(guildId)
            if (parties.isNotEmpty()) {
                return parties.first() // Return first active party found
            }
        }
        
        return null
    }
    
    override fun getPendingRequestsForGuild(guildId: UUID): Set<PartyRequest> {
        return partyRequestRepository.getPendingRequestsForGuild(guildId)
    }
    
    override fun getPendingRequestsFromGuild(guildId: UUID): Set<PartyRequest> {
        return partyRequestRepository.getPendingRequestsFromGuild(guildId)
    }
    
    override fun canManageParties(playerId: UUID, guildId: UUID): Boolean {
        return memberService.hasPermission(playerId, guildId, RankPermission.MANAGE_RELATIONS)
    }
    
    override fun processExpiredItems(): Int {
        var processedCount = 0
        
        try {
            // Process expired party requests
            val expiredRequests = partyRequestRepository.getExpiredRequests()
            for (request in expiredRequests) {
                val expiredRequest = request.copy(status = PartyRequestStatus.EXPIRED)
                if (partyRequestRepository.update(expiredRequest)) {
                    processedCount++
                    logger.info("Party request ${request.id} marked as expired")
                }
            }
            
            // Process expired parties
            val expiredParties = partyRepository.getExpiredParties()
            for (party in expiredParties) {
                val expiredParty = party.copy(status = PartyStatus.EXPIRED)
                if (partyRepository.update(expiredParty)) {
                    processedCount++
                    logger.info("Party ${party.id} marked as expired")
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing expired party items", e)
        }
        
        return processedCount
    }
    
    override fun setPartyName(partyId: UUID, name: String?, actorId: UUID): Boolean {
        try {
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                logger.warn("Party $partyId not found or not active")
                return false
            }
            
            // Check if actor has permission in any participating guild
            var hasPermission = false
            for (guildId in party.guildIds) {
                if (canManageParties(actorId, guildId)) {
                    hasPermission = true
                    break
                }
            }
            
            if (!hasPermission) {
                logger.warn("Player $actorId does not have permission to name party $partyId")
                return false
            }
            
            val updatedParty = party.copy(name = name)
            val success = partyRepository.update(updatedParty)
            
            if (success) {
                logger.info("Party $partyId name set to '$name' by player $actorId")
            }
            
            return success
        } catch (e: Exception) {
            logger.error("Error setting party name", e)
            return false
        }
    }
    
    override fun setPartyExpiration(partyId: UUID, duration: Duration, actorId: UUID): Boolean {
        try {
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                logger.warn("Party $partyId not found or not active")
                return false
            }
            
            // Check if actor has permission in any participating guild
            var hasPermission = false
            for (guildId in party.guildIds) {
                if (canManageParties(actorId, guildId)) {
                    hasPermission = true
                    break
                }
            }
            
            if (!hasPermission) {
                logger.warn("Player $actorId does not have permission to set expiration for party $partyId")
                return false
            }
            
            val expiresAt = Instant.now().plus(duration)
            val updatedParty = party.copy(expiresAt = expiresAt)
            val success = partyRepository.update(updatedParty)
            
            if (success) {
                logger.info("Party $partyId expiration set to $expiresAt by player $actorId")
            }
            
            return success
        } catch (e: Exception) {
            logger.error("Error setting party expiration", e)
            return false
        }
    }
    
    override fun getOnlinePartyMembers(partyId: UUID): Set<UUID> {
        val party = partyRepository.getById(partyId)
        if (party == null || !party.isActive()) {
            return emptySet()
        }
        
        val onlineMembers = mutableSetOf<UUID>()
        
        for (guildId in party.guildIds) {
            val guildMembers = memberService.getGuildMembers(guildId)
            for (member in guildMembers) {
                val player = Bukkit.getPlayer(member.playerId)
                if (player != null && player.isOnline) {
                    onlineMembers.add(member.playerId)
                }
            }
        }
        
        return onlineMembers
    }

    override fun setPartyRoleRestrictions(partyId: UUID, restrictedRoles: Set<UUID>?, actorId: UUID): Boolean {
        try {
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                logger.warn("Party $partyId not found or not active")
                return false
            }

            // Check if actor has permission in any participating guild
            var hasPermission = false
            for (guildId in party.guildIds) {
                if (canManageParties(actorId, guildId)) {
                    hasPermission = true
                    break
                }
            }

            if (!hasPermission) {
                logger.warn("Player $actorId does not have permission to set role restrictions for party $partyId")
                return false
            }

            val updatedParty = party.copy(restrictedRoles = restrictedRoles)
            val success = partyRepository.update(updatedParty)

            if (success) {
                logger.info("Party $partyId role restrictions set by player $actorId: ${restrictedRoles?.size ?: 0} roles")
            }

            return success
        } catch (e: Exception) {
            logger.error("Error setting party role restrictions", e)
            return false
        }
    }

    override fun canPlayerJoinParty(partyId: UUID, playerId: UUID): Boolean {
        try {
            val party = partyRepository.getById(partyId)
            if (party == null || !party.isActive()) {
                return false
            }

            // Check if player is a member of any participating guild
            var playerGuildId: UUID? = null
            var playerRankId: UUID? = null

            for (guildId in party.guildIds) {
                val member = memberService.getMember(playerId, guildId)
                if (member != null) {
                    playerGuildId = guildId
                    playerRankId = member.rankId
                    break
                }
            }

            // If player is not in any participating guild, they can't join
            if (playerGuildId == null) {
                return false
            }

            // Check role restrictions
            return party.canPlayerJoin(playerRankId!!)
        } catch (e: Exception) {
            logger.error("Error checking if player can join party", e)
            return false
        }
    }
}
