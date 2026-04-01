package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Hytale implementation of MemberService.
 *
 * This service provides member management operations including:
 * - Adding and removing members
 * - Changing member ranks
 * - Promoting and demoting members
 * - Permission checking
 * - Ownership transfer
 */
class HytaleMemberService(
    private val memberRepository: MemberRepository,
    private val rankRepository: RankRepository,
    private val guildRepository: GuildRepository
) : MemberService {

    private val log = LoggerFactory.getLogger(HytaleMemberService::class.java)

    override fun addMember(playerId: UUID, guildId: UUID, rankId: UUID): Member? {
        log.debug("Adding player $playerId to guild $guildId with rank $rankId")

        // Check if player is already a member
        if (memberRepository.isPlayerInGuild(playerId, guildId)) {
            log.debug("Player $playerId is already a member of guild $guildId")
            return null
        }

        // Verify guild exists
        if (guildRepository.getById(guildId) == null) {
            log.debug("Guild $guildId does not exist")
            return null
        }

        // Verify rank exists and belongs to this guild
        val rank = rankRepository.getById(rankId)
        if (rank == null || rank.guildId != guildId) {
            log.debug("Rank $rankId does not exist or does not belong to guild $guildId")
            return null
        }

        // Create and save the member
        val member = Member(
            playerId = playerId,
            guildId = guildId,
            rankId = rankId,
            joinedAt = Instant.now()
        )

        if (!memberRepository.add(member)) {
            log.error("Failed to add member to database")
            return null
        }

        log.info("Added player $playerId to guild $guildId with rank ${rank.name}")
        return member
    }

    override fun removeMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        log.debug("Attempting to remove player $playerId from guild $guildId by actor $actorId")

        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            log.debug("Actor $actorId does not have MANAGE_MEMBERS permission in guild $guildId")
            return false
        }

        // Can't remove yourself using this method (use leave guild instead)
        if (playerId == actorId) {
            log.debug("Cannot remove yourself using removeMember - use leave guild instead")
            return false
        }

        // Check if target is a member
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId)
        if (member == null) {
            log.debug("Player $playerId is not a member of guild $guildId")
            return false
        }

        // Can't remove the owner
        val guild = guildRepository.getById(guildId)
        if (guild != null && isGuildOwner(playerId, guildId)) {
            log.debug("Cannot remove the guild owner")
            return false
        }

        // Remove the member
        if (!memberRepository.remove(playerId, guildId)) {
            log.error("Failed to remove member from database")
            return false
        }

        log.info("Removed player $playerId from guild $guildId")
        return true
    }

    override fun changeMemberRank(playerId: UUID, guildId: UUID, newRankId: UUID, actorId: UUID): Boolean {
        log.debug("Attempting to change rank of player $playerId to $newRankId in guild $guildId by actor $actorId")

        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            log.debug("Actor $actorId does not have MANAGE_RANKS permission in guild $guildId")
            return false
        }

        // Get the member
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId)
        if (member == null) {
            log.debug("Player $playerId is not a member of guild $guildId")
            return false
        }

        // Verify new rank exists and belongs to this guild
        val newRank = rankRepository.getById(newRankId)
        if (newRank == null || newRank.guildId != guildId) {
            log.debug("Rank $newRankId does not exist or does not belong to guild $guildId")
            return false
        }

        // Can't change owner's rank
        if (isGuildOwner(playerId, guildId)) {
            log.debug("Cannot change the guild owner's rank")
            return false
        }

        // Update the member's rank
        val updated = member.copy(rankId = newRankId)
        if (!memberRepository.update(updated)) {
            log.error("Failed to update member rank in database")
            return false
        }

        log.info("Changed rank of player $playerId to ${newRank.name} in guild $guildId")
        return true
    }

    override fun getMember(playerId: UUID, guildId: UUID): Member? {
        return memberRepository.getByPlayerAndGuild(playerId, guildId)
    }

    override fun getGuildMembers(guildId: UUID): Set<Member> {
        return memberRepository.getByGuild(guildId)
    }

    override fun getPlayerGuilds(playerId: UUID): Set<UUID> {
        return memberRepository.getGuildsByPlayer(playerId)
    }

    override fun getPlayerRankId(playerId: UUID, guildId: UUID): UUID? {
        return memberRepository.getRankId(playerId, guildId)
    }

    override fun getMembersByRank(guildId: UUID, rankId: UUID): Set<Member> {
        return memberRepository.getByRank(guildId, rankId)
    }

    override fun getMemberCount(guildId: UUID): Int {
        return memberRepository.getMemberCount(guildId)
    }

    override fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean {
        return memberRepository.isPlayerInGuild(playerId, guildId)
    }

    override fun getTotalMemberCount(): Int {
        return memberRepository.getTotalCount()
    }

    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        // Check if player is the owner (has highest rank)
        if (isGuildOwner(playerId, guildId)) {
            return true
        }

        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false

        return rank.permissions.contains(permission)
    }

    override fun promoteMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        log.debug("Attempting to promote player $playerId in guild $guildId by actor $actorId")

        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            log.debug("Actor $actorId does not have MANAGE_RANKS permission in guild $guildId")
            return false
        }

        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val currentRank = rankRepository.getById(member.rankId) ?: return false

        // Can't promote the owner
        if (isGuildOwner(playerId, guildId)) {
            log.debug("Cannot promote the guild owner")
            return false
        }

        // Get all ranks for the guild sorted by priority (higher priority = higher rank)
        val ranks = rankRepository.getByGuild(guildId).sortedByDescending { it.priority }

        // Find the next higher rank
        val currentIndex = ranks.indexOfFirst { it.id == currentRank.id }
        if (currentIndex == -1 || currentIndex == 0) {
            log.debug("Player is already at the highest non-owner rank")
            return false
        }

        val nextRank = ranks[currentIndex - 1]

        // Update the member's rank
        val updated = member.copy(rankId = nextRank.id)
        if (!memberRepository.update(updated)) {
            log.error("Failed to update member rank in database")
            return false
        }

        log.info("Promoted player $playerId from ${currentRank.name} to ${nextRank.name} in guild $guildId")
        return true
    }

    override fun demoteMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
        log.debug("Attempting to demote player $playerId in guild $guildId by actor $actorId")

        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            log.debug("Actor $actorId does not have MANAGE_RANKS permission in guild $guildId")
            return false
        }

        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val currentRank = rankRepository.getById(member.rankId) ?: return false

        // Can't demote the owner
        if (isGuildOwner(playerId, guildId)) {
            log.debug("Cannot demote the guild owner")
            return false
        }

        // Get all ranks for the guild sorted by priority (higher priority = higher rank)
        val ranks = rankRepository.getByGuild(guildId).sortedByDescending { it.priority }

        // Find the next lower rank
        val currentIndex = ranks.indexOfFirst { it.id == currentRank.id }
        if (currentIndex == -1 || currentIndex == ranks.size - 1) {
            log.debug("Player is already at the lowest rank")
            return false
        }

        val nextRank = ranks[currentIndex + 1]

        // Update the member's rank
        val updated = member.copy(rankId = nextRank.id)
        if (!memberRepository.update(updated)) {
            log.error("Failed to update member rank in database")
            return false
        }

        log.info("Demoted player $playerId from ${currentRank.name} to ${nextRank.name} in guild $guildId")
        return true
    }

    override fun transferOwnership(guildId: UUID, currentOwnerId: UUID, newOwnerId: UUID): Boolean {
        log.debug("Attempting to transfer ownership of guild $guildId from $currentOwnerId to $newOwnerId")

        // Verify current owner
        if (!isGuildOwner(currentOwnerId, guildId)) {
            log.debug("Player $currentOwnerId is not the owner of guild $guildId")
            return false
        }

        // Verify new owner is a member
        val newOwnerMember = memberRepository.getByPlayerAndGuild(newOwnerId, guildId)
        if (newOwnerMember == null) {
            log.debug("Player $newOwnerId is not a member of guild $guildId")
            return false
        }

        val currentOwnerMember = memberRepository.getByPlayerAndGuild(currentOwnerId, guildId) ?: return false

        // Get the owner rank (highest priority)
        val ownerRank = rankRepository.getHighestRank(guildId)
        if (ownerRank == null) {
            log.error("Could not find owner rank for guild $guildId")
            return false
        }

        // Get the second highest rank for the old owner
        val ranks = rankRepository.getByGuild(guildId).sortedByDescending { it.priority }
        val secondHighestRank = if (ranks.size > 1) ranks[1] else ranks[0]

        // Update new owner to owner rank
        val updatedNewOwner = newOwnerMember.copy(rankId = ownerRank.id)
        if (!memberRepository.update(updatedNewOwner)) {
            log.error("Failed to update new owner rank")
            return false
        }

        // Update old owner to second highest rank
        val updatedOldOwner = currentOwnerMember.copy(rankId = secondHighestRank.id)
        if (!memberRepository.update(updatedOldOwner)) {
            log.error("Failed to update old owner rank")
            // Rollback new owner update
            memberRepository.update(newOwnerMember)
            return false
        }

        log.info("Transferred ownership of guild $guildId from $currentOwnerId to $newOwnerId")
        return true
    }

    private fun isGuildOwner(playerId: UUID, guildId: UUID): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        val highestRank = rankRepository.getHighestRank(guildId) ?: return false
        return rank.id == highestRank.id
    }
}
