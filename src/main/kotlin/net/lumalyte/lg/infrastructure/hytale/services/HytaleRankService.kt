package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/**
 * Hytale implementation of RankService.
 *
 * Handles guild rank operations including creating, updating, and managing rank permissions.
 */
class HytaleRankService(
    private val rankRepository: RankRepository,
    private val memberRepository: MemberRepository
) : RankService {

    override fun listRanks(guildId: UUID): Set<Rank> {
        return rankRepository.getByGuild(guildId)
    }

    override fun addRank(guildId: UUID, name: String, permissions: Set<RankPermission>, actorId: UUID): Rank? {
        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            return null
        }

        // Check if name is already taken
        if (rankRepository.isNameTaken(guildId, name)) {
            return null
        }

        // Get the next available priority
        val priority = rankRepository.getNextPriority(guildId)

        // Create the rank
        val rank = Rank(
            id = UUID.randomUUID(),
            guildId = guildId,
            name = name,
            priority = priority,
            permissions = permissions
        )

        return if (rankRepository.add(rank)) rank else null
    }

    override fun renameRank(rankId: UUID, newName: String, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false

        // Check if actor has permission
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            return false
        }

        // Check if new name is already taken
        if (rankRepository.isNameTaken(rank.guildId, newName)) {
            return false
        }

        // Update the rank
        val updatedRank = rank.copy(name = newName)
        return rankRepository.update(updatedRank)
    }

    override fun deleteRank(rankId: UUID, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false

        // Check if actor has permission
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            return false
        }

        // Can't delete the highest rank (owner rank)
        val highestRank = rankRepository.getHighestRank(rank.guildId)
        if (highestRank?.id == rankId) {
            return false
        }

        // Can't delete the default rank
        val defaultRank = rankRepository.getDefaultRank(rank.guildId)
        if (defaultRank?.id == rankId) {
            return false
        }

        // Check if any members have this rank
        val membersWithRank = memberRepository.getByRank(rank.guildId, rankId)
        if (membersWithRank.isNotEmpty()) {
            // Move members to default rank
            val newRankId = defaultRank?.id ?: return false
            membersWithRank.forEach { member ->
                memberRepository.update(member.copy(rankId = newRankId))
            }
        }

        return rankRepository.remove(rankId)
    }

    override fun setRankPermissions(rankId: UUID, permissions: Set<RankPermission>, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false

        // Check if actor has permission
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            return false
        }

        // Update the rank
        val updatedRank = rank.copy(permissions = permissions)
        return rankRepository.update(updatedRank)
    }

    override fun addRankPermission(rankId: UUID, permission: RankPermission, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false

        // Check if actor has permission
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            return false
        }

        // Add the permission
        val updatedPermissions = rank.permissions + permission
        val updatedRank = rank.copy(permissions = updatedPermissions)
        return rankRepository.update(updatedRank)
    }

    override fun removeRankPermission(rankId: UUID, permission: RankPermission, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false

        // Check if actor has permission
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            return false
        }

        // Remove the permission
        val updatedPermissions = rank.permissions - permission
        val updatedRank = rank.copy(permissions = updatedPermissions)
        return rankRepository.update(updatedRank)
    }

    override fun assignRank(playerId: UUID, guildId: UUID, rankId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            return false
        }

        // Get the member
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false

        // Get the rank to ensure it exists and belongs to this guild
        val rank = rankRepository.getById(rankId) ?: return false
        if (rank.guildId != guildId) {
            return false
        }

        // Can't change the owner's rank
        val highestRank = rankRepository.getHighestRank(guildId)
        if (member.rankId == highestRank?.id) {
            return false
        }

        // Update the member
        val updatedMember = member.copy(rankId = rankId)
        return memberRepository.update(updatedMember)
    }

    override fun getPlayerRank(playerId: UUID, guildId: UUID): Rank? {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return null
        return rankRepository.getById(member.rankId)
    }

    override fun getRank(rankId: UUID): Rank? {
        return rankRepository.getById(rankId)
    }

    override fun getRankByName(guildId: UUID, name: String): Rank? {
        return rankRepository.getByName(guildId, name)
    }

    override fun getDefaultRank(guildId: UUID): Rank? {
        return rankRepository.getDefaultRank(guildId)
    }

    override fun getHighestRank(guildId: UUID): Rank? {
        return rankRepository.getHighestRank(guildId)
    }

    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        val rank = getPlayerRank(playerId, guildId) ?: return false
        return rank.permissions.contains(permission)
    }

    override fun getRankCount(guildId: UUID): Int {
        return rankRepository.getCountByGuild(guildId)
    }

    override fun updateRank(rank: Rank, actorId: UUID): Boolean {
        // Check if actor has permission
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            return false
        }

        return rankRepository.update(rank)
    }

    override fun createDefaultRanks(guildId: UUID, ownerId: UUID): Boolean {
        // Create Owner rank
        val ownerRank = Rank(
            id = UUID.randomUUID(),
            guildId = guildId,
            name = "Owner",
            priority = 0,
            permissions = RankPermission.values().toSet()
        )

        // Create Officer rank
        val officerRank = Rank(
            id = UUID.randomUUID(),
            guildId = guildId,
            name = "Officer",
            priority = 1,
            permissions = setOf(
                RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_CLAIMS,
                RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.ACCESS_VAULT,
                RankPermission.DEPOSIT_TO_VAULT,
                RankPermission.WITHDRAW_FROM_VAULT
            )
        )

        // Create Member rank (default)
        val memberRank = Rank(
            id = UUID.randomUUID(),
            guildId = guildId,
            name = "Member",
            priority = 2,
            permissions = setOf(
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.ACCESS_VAULT,
                RankPermission.DEPOSIT_TO_VAULT
            )
        )

        return rankRepository.add(ownerRank) &&
                rankRepository.add(officerRank) &&
                rankRepository.add(memberRank)
    }
}
