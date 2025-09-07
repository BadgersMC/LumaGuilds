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
    private val guildRepository: GuildRepository
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
            
            return member
        }
        
        logger.error("Failed to add player $playerId to guild $guildId")
        return null
    }
    
    override fun removeMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission to manage members
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
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
            logger.info("Player $playerId removed from guild $guildId by $actorId")

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
        
        // Check if new rank exists and belongs to the guild
        val newRank = rankRepository.getById(newRankId) ?: return false
        if (newRank.guildId != guildId) {
            logger.warn("Rank $newRankId doesn't belong to guild $guildId")
            return false
        }
        
        val updatedMember = member.copy(rankId = newRankId)
        val result = memberRepository.update(updatedMember)
        if (result) {
            logger.info("Player $playerId rank changed to '${newRank.name}' in guild $guildId by $actorId")
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
    
    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        return rank.permissions.contains(permission)
    }
}
