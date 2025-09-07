package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.slf4j.LoggerFactory
import java.util.UUID

class RankServiceBukkit(
    private val rankRepository: RankRepository,
    private val memberRepository: MemberRepository,
    private val guildRepository: GuildRepository,
    private val memberService: net.lumalyte.lg.application.services.MemberService
) : RankService {
    
    private val logger = LoggerFactory.getLogger(RankServiceBukkit::class.java)
    
    override fun listRanks(guildId: UUID): Set<Rank> = rankRepository.getByGuild(guildId)
    
    override fun addRank(guildId: UUID, name: String, permissions: Set<RankPermission>, actorId: UUID): Rank? {
        // Check if actor has permission to manage ranks
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to add rank to guild $guildId without permission")
            return null
        }
        
        // Validate rank name
        if (name.isBlank() || name.length > 24) {
            logger.warn("Invalid rank name: $name")
            return null
        }
        
        // Check if name is already taken in this guild
        if (rankRepository.isNameTaken(guildId, name)) {
            logger.warn("Rank name already taken in guild: $name")
            return null
        }
        
        // Create rank
        val rankId = UUID.randomUUID()
        val priority = rankRepository.getNextPriority(guildId)
        val rank = Rank(
            id = rankId,
            guildId = guildId,
            name = name,
            priority = priority,
            permissions = permissions
        )
        
        val result = rankRepository.add(rank)
        if (result) {
            logger.info("Added rank '$name' to guild $guildId by $actorId")
            return rank
        }
        
        logger.error("Failed to add rank '$name' to guild $guildId")
        return null
    }
    
    override fun renameRank(rankId: UUID, newName: String, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false
        
        // Check if actor has permission to manage ranks
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to rename rank $rankId without permission")
            return false
        }
        
        // Validate new name
        if (newName.isBlank() || newName.length > 24) {
            logger.warn("Invalid rank name: $newName")
            return false
        }
        
        // Check if new name is already taken in this guild
        if (rankRepository.isNameTaken(rank.guildId, newName)) {
            logger.warn("Rank name already taken in guild: $newName")
            return false
        }
        
        val updatedRank = rank.copy(name = newName)
        val result = rankRepository.update(updatedRank)
        if (result) {
            logger.info("Rank $rankId renamed to '$newName' by $actorId")
        }
        return result
    }
    
    override fun deleteRank(rankId: UUID, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false
        
        // Check if actor has permission to manage ranks
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to delete rank $rankId without permission")
            return false
        }
        
        // Check if rank is in use by any members
        val membersWithRank = memberRepository.getByRank(rank.guildId, rankId)
        if (membersWithRank.isNotEmpty()) {
            logger.warn("Cannot delete rank $rankId - it has ${membersWithRank.size} members")
            return false
        }
        
        val result = rankRepository.remove(rankId)
        if (result) {
            logger.info("Rank $rankId deleted by $actorId")
        }
        return result
    }
    
    override fun setRankPermissions(rankId: UUID, permissions: Set<RankPermission>, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false
        
        // Check if actor has permission to manage ranks
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to set permissions for rank $rankId without permission")
            return false
        }
        
        val updatedRank = rank.copy(permissions = permissions)
        val result = rankRepository.update(updatedRank)
        if (result) {
            logger.info("Permissions for rank $rankId set by $actorId: ${permissions.map { it.name }}")
        }
        return result
    }
    
    override fun addRankPermission(rankId: UUID, permission: RankPermission, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false
        
        // Check if actor has permission to manage ranks
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to add permission to rank $rankId without permission")
            return false
        }
        
        if (rank.permissions.contains(permission)) {
            logger.warn("Rank $rankId already has permission ${permission.name}")
            return false
        }
        
        val updatedPermissions = rank.permissions + permission
        val updatedRank = rank.copy(permissions = updatedPermissions)
        val result = rankRepository.update(updatedRank)
        if (result) {
            logger.info("Permission ${permission.name} added to rank $rankId by $actorId")
        }
        return result
    }
    
    override fun removeRankPermission(rankId: UUID, permission: RankPermission, actorId: UUID): Boolean {
        val rank = rankRepository.getById(rankId) ?: return false
        
        // Check if actor has permission to manage ranks
        if (!hasPermission(actorId, rank.guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to remove permission from rank $rankId without permission")
            return false
        }
        
        if (!rank.permissions.contains(permission)) {
            logger.warn("Rank $rankId doesn't have permission ${permission.name}")
            return false
        }
        
        val updatedPermissions = rank.permissions - permission
        val updatedRank = rank.copy(permissions = updatedPermissions)
        val result = rankRepository.update(updatedRank)
        if (result) {
            logger.info("Permission ${permission.name} removed from rank $rankId by $actorId")
        }
        return result
    }
    
    override fun assignRank(playerId: UUID, guildId: UUID, rankId: UUID, actorId: UUID): Boolean {
        // Check if actor has permission to manage members
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
            logger.warn("Player $actorId attempted to assign rank to player $playerId without permission")
            return false
        }
        
        // Check if rank exists and belongs to the guild
        val rank = rankRepository.getById(rankId) ?: return false
        if (rank.guildId != guildId) {
            logger.warn("Rank $rankId doesn't belong to guild $guildId")
            return false
        }
        
        // Check if player is already a member
        val existingMember = memberRepository.getByPlayerAndGuild(playerId, guildId)
        if (existingMember != null) {
            // Update existing member's rank
            val updatedMember = existingMember.copy(rankId = rankId)
            val result = memberRepository.update(updatedMember)
            if (result) {
                logger.info("Player $playerId rank changed to '${rank.name}' in guild $guildId by $actorId")
            }
            return result
        } else {
            // Add new member
            val member = memberService.addMember(playerId, guildId, rankId)
            if (member != null) {
                logger.info("Player $playerId added to guild $guildId with rank '${rank.name}' by $actorId")
                return true
            }
            return false
        }
    }
    
    override fun getPlayerRank(playerId: UUID, guildId: UUID): Rank? {
        val rankId = memberRepository.getRankId(playerId, guildId) ?: return null
        return rankRepository.getById(rankId)
    }
    
    override fun getRank(rankId: UUID): Rank? = rankRepository.getById(rankId)
    
    override fun getRankByName(guildId: UUID, name: String): Rank? = rankRepository.getByName(guildId, name)
    
    override fun getDefaultRank(guildId: UUID): Rank? = rankRepository.getDefaultRank(guildId)
    
    override fun getHighestRank(guildId: UUID): Rank? = rankRepository.getHighestRank(guildId)
    
    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        val rank = getPlayerRank(playerId, guildId) ?: return false
        return rank.permissions.contains(permission)
    }
    
    override fun getRankCount(guildId: UUID): Int = rankRepository.getCountByGuild(guildId)

    override fun updateRank(rank: Rank): Boolean {
        // Check if rank exists
        val existingRank = rankRepository.getById(rank.id) ?: return false

        // Validate that the rank belongs to the same guild
        if (existingRank.guildId != rank.guildId) {
            logger.warn("Cannot update rank ${rank.id} - guild mismatch")
            return false
        }

        val result = rankRepository.update(rank)
        if (result) {
            logger.info("Rank ${rank.id} updated: name='${rank.name}', permissions=${rank.permissions.size}, icon='${rank.icon}'")
        }
        return result
    }

    override fun createDefaultRanks(guildId: UUID, ownerId: UUID): Boolean {
        val defaultRanks = listOf(
            Rank(UUID.randomUUID(), guildId, "Owner", 0, RankPermission.values().toSet()),
            Rank(UUID.randomUUID(), guildId, "Co-Owner", 1, setOf(
                RankPermission.MANAGE_RANKS,
                RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER,
                RankPermission.MANAGE_HOME,
                RankPermission.MANAGE_MODE,
                RankPermission.MANAGE_RELATIONS,
                RankPermission.DECLARE_WAR,
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.WITHDRAW_FROM_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS,
                RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.SEND_PINGS,
                RankPermission.MANAGE_CLAIMS,
                RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS
            )),
            Rank(UUID.randomUUID(), guildId, "Admin", 2, setOf(
                RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER,
                RankPermission.MANAGE_HOME,
                RankPermission.MANAGE_RELATIONS,
                RankPermission.DECLARE_WAR,
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS,
                RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.SEND_PINGS,
                RankPermission.MANAGE_CLAIMS,
                RankPermission.MANAGE_FLAGS
            )),
            Rank(UUID.randomUUID(), guildId, "Mod", 3, setOf(
                RankPermission.MANAGE_MEMBERS,
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS,
                RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.MANAGE_CLAIMS
            )),
            Rank(UUID.randomUUID(), guildId, "Member", 4, setOf(
                RankPermission.VIEW_BANK_TRANSACTIONS
            ))
        )
        
        var success = true
        for (rank in defaultRanks) {
            if (!rankRepository.add(rank)) {
                logger.error("Failed to create default rank: ${rank.name}")
                success = false
                break
            }
        }
        
        if (success) {
            logger.info("Created default ranks for guild $guildId")
        }
        return success
    }
}
