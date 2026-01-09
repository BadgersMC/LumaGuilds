package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.events.GuildMemberJoinEvent
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class MemberServiceBukkit(
    private val memberRepository: MemberRepository,
    private val rankRepository: RankRepository,
    private val guildRepository: GuildRepository,
    private val progressionRepository: net.lumalyte.lg.application.persistence.ProgressionRepository,
    private val progressionConfigService: ProgressionConfigService
) : MemberService {

    private val logger = LoggerFactory.getLogger(MemberServiceBukkit::class.java)
    
    override fun addMember(playerId: UUID, guildId: UUID, rankId: UUID): Member? {
        // Check if guild exists
        if (guildRepository.getById(guildId) == null) {
            logger.warn("Attempted to add member to non-existent guild: $guildId")
            return null
        }
        
        // Check if rank exists and belongs to the guild
        val rank = rankRepository.getById(rankId) ?: return null
        if (rank.guildId != guildId) {
            logger.warn("Rank $rankId doesn't belong to guild $guildId")
            return null
        }
        
        // Check if player is already a member
        if (memberRepository.isPlayerInGuild(playerId, guildId)) {
            logger.warn("Player $playerId is already a member of guild $guildId")
            return null
        }

        // Check guild member limit (progression-based)
        val currentMemberCount = memberRepository.getByGuild(guildId).size
        val progression = progressionRepository.getGuildProgression(guildId)
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()
        var maxMembers = 10 // Default starting member limit
        if (progression != null) {
            for (level in 1..progression.currentLevel) {
                val members = levelRewards[level]?.members ?: 10
                if (members > maxMembers) maxMembers = members
            }
        }
        if (currentMemberCount >= maxMembers) {
            logger.warn("Guild $guildId has reached member limit: $currentMemberCount/$maxMembers")
            return null
        }

        val member = Member(
            playerId = playerId,
            guildId = guildId,
            rankId = rankId,
            joinedAt = Instant.now()
        )
        
        val result = memberRepository.add(member)
        if (result) {
            logger.info("Player $playerId added to guild $guildId with rank '${rank.name}'")

            // Fire event for progression system
            Bukkit.getPluginManager().callEvent(GuildMemberJoinEvent(guildId, playerId))

            // Update Apollo team and waypoints (if available)
            try {
                val guildTeamService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService>()
                guildTeamService?.onPlayerJoinGuild(playerId, guildId)

                val guildWaypointService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService>()
                val player = org.bukkit.Bukkit.getPlayer(playerId)
                if (player != null) {
                    guildWaypointService?.showGuildHomeWaypoints(player)
                }
            } catch (e: Exception) {
                // Silently fail if Apollo not available
                logger.debug("Apollo update skipped: ${e.message}")
            }

            return member
        }
        
        logger.error("Failed to add player $playerId to guild $guildId")
        return null
    }
    
    override fun removeMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission to manage members
        // EXCEPTION: Players can always leave voluntarily (remove themselves)
        if (actorId != playerId && !hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            logger.warn("Player $actorId attempted to remove member $playerId without permission")
            return false
        }

        // Check if player is actually a member
        if (!memberRepository.isPlayerInGuild(playerId, guildId)) {
            logger.warn("Player $playerId is not a member of guild $guildId")
            return false
        }

        val result = memberRepository.remove(playerId, guildId)
        if (result) {
            // Update Apollo team and waypoints (if available)
            try {
                val guildTeamService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService>()
                guildTeamService?.onPlayerLeaveGuild(playerId, guildId)

                val guildWaypointService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService>()
                val player = org.bukkit.Bukkit.getPlayer(playerId)
                if (player != null) {
                    // Refresh waypoints to remove old guild's homes
                    guildWaypointService?.showGuildHomeWaypoints(player)
                }
            } catch (e: Exception) {
                // Silently fail if Apollo not available
                logger.debug("Apollo update skipped: ${e.message}")
            }

            // Send Apollo notification (if available)
            try {
                val notificationService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>()
                val player = org.bukkit.Bukkit.getPlayer(playerId)
                val playerName = player?.name ?: "Player"
                val wasKicked = actorId != playerId

                notificationService?.notifyMemberLeave(guildId, playerName, wasKicked)
            } catch (e: Exception) {
                // Silently fail if Apollo not available
                logger.debug("Apollo notification skipped: ${e.message}")
            }

            if (actorId == playerId) {
                logger.info("Player $playerId left guild $guildId")
            } else {
                logger.info("Player $playerId removed from guild $guildId by $actorId")
            }

            // Note: Party preference cleanup is handled by the PartyChatCommand's clearInvalidPartyReferences method
            // which runs periodically and cleans up preferences for disbanded parties
        }
        return result
    }
    
    override fun changeMemberRank(playerId: UUID, guildId: UUID, newRankId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission to manage members
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            logger.warn("Player $actorId attempted to change rank for member $playerId without permission")
            return false
        }

        // Check if player is a member
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val currentRank = rankRepository.getById(member.rankId) ?: return false

        // OWNER PROTECTION: Prevent owner from changing their own rank
        if (currentRank.priority == 0 && playerId == actorId) {
            logger.warn("Player $actorId (owner) attempted to change their own rank - ownership transfer required")
            return false
        }

        // Check if new rank exists and belongs to the guild
        val newRank = rankRepository.getById(newRankId) ?: return false
        if (newRank.guildId != guildId) {
            logger.warn("Rank $newRankId doesn't belong to guild $guildId")
            return false
        }

        // OWNER PROTECTION: Prevent promoting anyone to owner rank (priority 0) - use ownership transfer instead
        if (newRank.priority == 0) {
            logger.warn("Cannot directly promote to owner rank - use ownership transfer instead")
            return false
        }

        val updatedMember = member.copy(rankId = newRankId)
        val result = memberRepository.update(updatedMember)
        if (result) {
            logger.info("Player $playerId rank changed to '${newRank.name}' in guild $guildId by $actorId")

            // Send Apollo notification (if available)
            try {
                val notificationService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>()
                val actor = org.bukkit.Bukkit.getPlayer(actorId)
                val actorName = actor?.name ?: "Admin"

                // Determine if it's a promotion or demotion
                if (newRank.priority < currentRank.priority) {
                    // Lower priority number = higher rank = promotion
                    notificationService?.notifyPromotion(playerId, guildId, newRank.name, actorName)
                } else {
                    // Higher priority number = lower rank = demotion
                    notificationService?.notifyDemotion(playerId, guildId, newRank.name, actorName)
                }
            } catch (e: Exception) {
                // Silently fail if Apollo not available
                logger.debug("Apollo notification skipped: ${e.message}")
            }
        }
        return result
    }
    
    override fun getMember(playerId: UUID, guildId: UUID): Member? = 
        memberRepository.getByPlayerAndGuild(playerId, guildId)
    
    override fun getGuildMembers(guildId: UUID): Set<Member> = memberRepository.getByGuild(guildId)
    
    override fun getPlayerGuilds(playerId: UUID): Set<UUID> = memberRepository.getGuildsByPlayer(playerId)
    
    override fun getPlayerRankId(playerId: UUID, guildId: UUID): UUID? = 
        memberRepository.getRankId(playerId, guildId)
    
    override fun getMembersByRank(guildId: UUID, rankId: UUID): Set<Member> = 
        memberRepository.getByRank(guildId, rankId)
    
    override fun getMemberCount(guildId: UUID): Int = memberRepository.getMemberCount(guildId)
    
    override fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean = 
        memberRepository.isPlayerInGuild(playerId, guildId)
    
    override fun getTotalMemberCount(): Int = memberRepository.getTotalCount()
    
    override fun promoteMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission to manage members
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            logger.warn("Player $actorId attempted to promote member $playerId without permission")
            return false
        }

        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val currentRank = rankRepository.getById(member.rankId) ?: return false

        // Get all ranks for the guild, sorted by priority (ascending)
        val guildRanks = rankRepository.getByGuild(guildId).sortedBy { it.priority }

        // Find the next rank (lower priority number = higher rank)
        val currentIndex = guildRanks.indexOfFirst { it.id == currentRank.id }
        if (currentIndex <= 0) {
            logger.warn("Player $playerId is already at the highest rank")
            return false
        }

        val nextRank = guildRanks[currentIndex - 1]

        // OWNER PROTECTION: Prevent promoting anyone to owner rank (priority 0) - use ownership transfer instead
        if (nextRank.priority == 0) {
            logger.warn("Cannot promote to owner rank - use ownership transfer instead")
            return false
        }

        val updatedMember = member.copy(rankId = nextRank.id)
        val result = memberRepository.update(updatedMember)
        if (result) {
            logger.info("Player $playerId promoted from '${currentRank.name}' to '${nextRank.name}' in guild $guildId by $actorId")
        }
        return result
    }
    
    override fun demoteMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission to manage members
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            logger.warn("Player $actorId attempted to demote member $playerId without permission")
            return false
        }

        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val currentRank = rankRepository.getById(member.rankId) ?: return false

        // OWNER PROTECTION: Prevent owner from demoting themselves
        if (currentRank.priority == 0 && playerId == actorId) {
            logger.warn("Player $actorId (owner) attempted to demote themselves - ownership transfer required")
            return false
        }

        // Get all ranks for the guild, sorted by priority (ascending)
        val guildRanks = rankRepository.getByGuild(guildId).sortedBy { it.priority }

        // Find the next rank (higher priority number = lower rank)
        val currentIndex = guildRanks.indexOfFirst { it.id == currentRank.id }
        if (currentIndex >= guildRanks.size - 1) {
            logger.warn("Player $playerId is already at the lowest rank")
            return false
        }

        val nextRank = guildRanks[currentIndex + 1]
        val updatedMember = member.copy(rankId = nextRank.id)
        val result = memberRepository.update(updatedMember)
        if (result) {
            logger.info("Player $playerId demoted from '${currentRank.name}' to '${nextRank.name}' in guild $guildId by $actorId")
        }
        return result
    }

    override fun transferOwnership(guildId: UUID, currentOwnerId: UUID, newOwnerId: UUID): Boolean {
        // Verify current owner is actually the owner
        val currentOwner = memberRepository.getByPlayerAndGuild(currentOwnerId, guildId) ?: run {
            logger.warn("Current owner $currentOwnerId not found in guild $guildId")
            return false
        }
        val currentOwnerRank = rankRepository.getById(currentOwner.rankId) ?: run {
            logger.warn("Current owner rank not found")
            return false
        }
        if (currentOwnerRank.priority != 0) {
            logger.warn("Player $currentOwnerId is not the owner (priority ${currentOwnerRank.priority})")
            return false
        }

        // Verify new owner exists and is a member
        val newOwner = memberRepository.getByPlayerAndGuild(newOwnerId, guildId) ?: run {
            logger.warn("New owner $newOwnerId not found in guild $guildId")
            return false
        }

        // Cannot transfer to self
        if (currentOwnerId == newOwnerId) {
            logger.warn("Cannot transfer ownership to self")
            return false
        }

        // Get all ranks sorted by priority
        val guildRanks = rankRepository.getByGuild(guildId).sortedBy { it.priority }
        if (guildRanks.size < 2) {
            logger.warn("Guild $guildId doesn't have enough ranks for ownership transfer")
            return false
        }

        val ownerRank = guildRanks.first() // Priority 0
        val secondHighestRank = guildRanks[1] // Usually co-owner

        // Atomically update both members
        try {
            // Demote current owner to second-highest rank
            val demotedOwner = currentOwner.copy(rankId = secondHighestRank.id)
            if (!memberRepository.update(demotedOwner)) {
                logger.error("Failed to demote current owner")
                return false
            }

            // Promote new owner to owner rank
            val promotedNewOwner = newOwner.copy(rankId = ownerRank.id)
            if (!memberRepository.update(promotedNewOwner)) {
                logger.error("Failed to promote new owner - rolling back current owner demotion")
                // Rollback: restore current owner's rank
                memberRepository.update(currentOwner)
                return false
            }

            logger.info("Ownership of guild $guildId transferred from $currentOwnerId to $newOwnerId")
            return true
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error during ownership transfer: ${e.message}", e)
            return false
        }
    }

    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        return rank.permissions.contains(permission)
    }
}
