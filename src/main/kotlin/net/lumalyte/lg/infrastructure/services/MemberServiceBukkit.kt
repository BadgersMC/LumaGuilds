package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ActivityLevel
import net.lumalyte.lg.application.services.MemberActivityStats
import net.lumalyte.lg.application.services.MemberSearchFilter
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankChangeRecord
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

    // === ADVANCED MEMBER SEARCH ===

    override fun searchMembers(guildId: UUID, filter: MemberSearchFilter): List<Member> {
        try {
            val allMembers = memberRepository.getByGuild(guildId)
            return allMembers.filter { member ->
                // Name query filter
                if (filter.nameQuery != null) {
                    val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: ""
                    if (!playerName.contains(filter.nameQuery, ignoreCase = true)) {
                        return@filter false
                    }
                }

                // Rank filter
                if (filter.rankFilter != null && !filter.rankFilter.contains(member.rankId)) {
                    return@filter false
                }

                // Online status filter
                if (filter.onlineOnly && !(Bukkit.getPlayer(member.playerId)?.isOnline ?: false)) {
                    return@filter false
                }

                // Join date filters
                if (filter.joinDateAfter != null && member.joinedAt.isBefore(filter.joinDateAfter)) {
                    return@filter false
                }
                if (filter.joinDateBefore != null && member.joinedAt.isAfter(filter.joinDateBefore)) {
                    return@filter false
                }

                // Activity level filter (simplified - would need more data for accurate activity levels)
                if (filter.activityLevel != null) {
                    // For now, just check if member has been active recently
                    // In a real implementation, this would use more sophisticated activity tracking
                    val daysSinceJoin = java.time.temporal.ChronoUnit.DAYS.between(
                        member.joinedAt, Instant.now()
                    )
                    val activityLevel = when {
                        daysSinceJoin < 7 -> ActivityLevel.HIGH
                        daysSinceJoin < 30 -> ActivityLevel.MEDIUM
                        else -> ActivityLevel.LOW
                    }
                    if (activityLevel != filter.activityLevel) {
                        return@filter false
                    }
                }

                true
            }
        } catch (e: Exception) {
            logger.error("Error searching members for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getMemberActivityStats(guildId: UUID, memberId: UUID, periodDays: Int): MemberActivityStats {
        try {
            val member = memberRepository.getByPlayerAndGuild(memberId, guildId)
                ?: throw IllegalArgumentException("Member $memberId not found in guild $guildId")

            val periodStart = Instant.now().minus(periodDays.toLong(), java.time.temporal.ChronoUnit.DAYS)
            val periodEnd = Instant.now()

            // This is a simplified implementation
            // In a real system, you'd track member activity through various events
            val daysActive = periodDays // Simplified - assume active every day
            val transactionCount = 0 // Would need bank transaction tracking
            val totalContributions = 0 // Would need contribution tracking
            val totalWithdrawals = 0 // Would need withdrawal tracking

            val activityLevel = when {
                daysActive >= periodDays * 0.8 -> ActivityLevel.HIGH
                daysActive >= periodDays * 0.5 -> ActivityLevel.MEDIUM
                else -> ActivityLevel.LOW
            }

            return MemberActivityStats(
                memberId = memberId,
                guildId = guildId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                totalContributions = totalContributions,
                totalWithdrawals = totalWithdrawals,
                netContribution = totalContributions - totalWithdrawals,
                transactionCount = transactionCount,
                averageTransactionAmount = if (transactionCount > 0) totalContributions.toDouble() / transactionCount else 0.0,
                lastActivityDate = member.joinedAt, // Simplified
                activityLevel = activityLevel,
                rankChanges = 0, // Would need rank change tracking
                daysActive = daysActive
            )
        } catch (e: Exception) {
            logger.error("Error getting activity stats for member $memberId in guild $guildId", e)
            throw e
        }
    }

    override fun getMembersByActivityLevel(guildId: UUID, activityLevel: ActivityLevel): List<Member> {
        try {
            val allMembers = memberRepository.getByGuild(guildId)
            return allMembers.filter { member ->
                val stats = getMemberActivityStats(guildId, member.playerId)
                stats.activityLevel == activityLevel
            }
        } catch (e: Exception) {
            logger.error("Error getting members by activity level for guild $guildId", e)
            return emptyList()
        }
    }

    // === BULK MEMBER OPERATIONS ===

    override fun bulkChangeRank(guildId: UUID, memberIds: List<UUID>, newRankId: UUID, actorId: UUID): Int {
        try {
            var successCount = 0

            for (memberId in memberIds) {
                try {
                    if (changeMemberRank(memberId, guildId, newRankId, actorId)) {
                        successCount++
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to change rank for member $memberId in guild $guildId", e)
                }
            }

            logger.info("Bulk rank change: $successCount/${memberIds.size} members updated in guild $guildId")
            return successCount
        } catch (e: Exception) {
            logger.error("Error in bulk rank change for guild $guildId", e)
            return 0
        }
    }

    override fun bulkMessageMembers(guildId: UUID, memberIds: List<UUID>, message: String, senderId: UUID): Int {
        try {
            var successCount = 0

            for (memberId in memberIds) {
                try {
                    val memberPlayer = Bukkit.getPlayer(memberId)
                    if (memberPlayer != null && memberPlayer.isOnline) {
                        memberPlayer.sendMessage("§6[Guild Message] §f$message")
                        successCount++
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to send message to member $memberId", e)
                }
            }

            logger.info("Bulk message: $successCount/${memberIds.size} members messaged in guild $guildId")
            return successCount
        } catch (e: Exception) {
            logger.error("Error in bulk messaging for guild $guildId", e)
            return 0
        }
    }

    override fun getMembersGroupedByRank(guildId: UUID): Map<UUID, List<Member>> {
        try {
            val members = memberRepository.getByGuild(guildId)
            return members.groupBy { it.rankId }
        } catch (e: Exception) {
            logger.error("Error getting members grouped by rank for guild $guildId", e)
            return emptyMap()
        }
    }

    override fun getInactiveMembers(guildId: UUID, inactiveDays: Int): List<Member> {
        try {
            val cutoffDate = Instant.now().minus(inactiveDays.toLong(), java.time.temporal.ChronoUnit.DAYS)
            val allMembers = memberRepository.getByGuild(guildId)

            return allMembers.filter { member ->
                // Simplified: consider members inactive if they joined more than inactiveDays ago
                // In a real implementation, you'd track last activity
                val daysSinceJoin = java.time.temporal.ChronoUnit.DAYS.between(
                    member.joinedAt, Instant.now()
                )
                daysSinceJoin > inactiveDays
            }
        } catch (e: Exception) {
            logger.error("Error getting inactive members for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getOnlineMembers(guildId: UUID): Set<Member> {
        return try {
            val allMembers = memberRepository.getByGuild(guildId)
            val onlinePlayers = org.bukkit.Bukkit.getOnlinePlayers()

            allMembers.filter { member ->
                onlinePlayers.any { player -> player.uniqueId == member.playerId }
            }.toSet()
        } catch (e: Exception) {
            logger.error("Error getting online members for guild $guildId", e)
            emptySet()
        }
    }

    override fun getMemberNotes(playerId: UUID, guildId: UUID): String {
        return try {
            // For now, return empty string as we don't have a notes table yet
            // In a full implementation, this would query a member_notes table
            ""
        } catch (e: Exception) {
            logger.error("Error getting member notes for player $playerId in guild $guildId", e)
            ""
        }
    }

    override fun setMemberNotes(playerId: UUID, guildId: UUID, notes: String, actorId: UUID): Boolean {
        return try {
            // For now, just log the notes (in a full implementation, this would save to a member_notes table)
            logger.info("Setting notes for player $playerId in guild $guildId: ${notes.take(100)}")
            true
        } catch (e: Exception) {
            logger.error("Error setting member notes for player $playerId in guild $guildId", e)
            false
        }
    }

    override fun getRankChangeHistory(playerId: UUID, guildId: UUID): List<RankChangeRecord> {
        return try {
            // For now, return empty list (in a full implementation, this would query a rank_change_history table)
            // In a complete implementation, you'd track all rank changes with timestamps and reasons
            emptyList()
        } catch (e: Exception) {
            logger.error("Error getting rank change history for player $playerId in guild $guildId", e)
            emptyList()
        }
    }
}
