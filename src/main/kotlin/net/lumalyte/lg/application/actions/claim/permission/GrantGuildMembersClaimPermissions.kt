package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.results.claim.permission.GrantGuildMembersClaimPermissionsResult
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class GrantGuildMembersClaimPermissions(private val claimRepository: ClaimRepository,
                                       private val memberService: MemberService,
                                       private val playerAccessRepository: PlayerAccessRepository) {
    /**
     * Grants all available permissions to all members of the guild that owns the claim.
     *
     * @param claimId The UUID of the claim to share with guild members.
     * @param playerId The UUID of the personal owner or authorized guild permission manager.
     * @return A GrantGuildMembersClaimPermissionsResult indicating the outcome.
     */
    fun execute(claimId: UUID, playerId: UUID): GrantGuildMembersClaimPermissionsResult {
        // Check if claim exists and get it
        val claim = claimRepository.getById(claimId)
            ?: return GrantGuildMembersClaimPermissionsResult.ClaimNotFound

        // Personal owners remain authorized; guild-owned claims use current rank permissions.
        if (claim.playerId != playerId) {
            val guildId = claim.teamId ?: return GrantGuildMembersClaimPermissionsResult.NotClaimOwner
            if (!memberService.hasPermission(playerId, guildId, RankPermission.MANAGE_PERMISSIONS)) {
                return GrantGuildMembersClaimPermissionsResult.NotClaimOwner
            }
        }

        // Check if claim is guild-owned
        val guildId = claim.teamId
            ?: return GrantGuildMembersClaimPermissionsResult.ClaimNotGuildOwned

        // Get all guild members
        val guildMembers = memberService.getGuildMembers(guildId)
        if (guildMembers.isEmpty()) {
            return GrantGuildMembersClaimPermissionsResult.NoGuildMembers
        }

        // Grant permissions to all guild members except the historical personal owner,
        // who already has implicit claim access through claim.playerId.
        var grantedCount = 0
        var alreadyHadAccessCount = 0

        try {
            val allPermissions = ClaimPermission.entries

            for (member in guildMembers) {
                // The actor can be a guild manager; do not accidentally exclude them.
                if (member.playerId == claim.playerId) continue

                var memberGranted = false
                for (permission in allPermissions) {
                    if (playerAccessRepository.add(claimId, member.playerId, permission)) {
                        memberGranted = true
                    }
                }

                if (memberGranted) {
                    grantedCount++
                } else {
                    alreadyHadAccessCount++
                }
            }

            return GrantGuildMembersClaimPermissionsResult.Success(grantedCount, alreadyHadAccessCount)

        } catch (error: DatabaseOperationException) {
            println("Error granting guild permissions: ${error.message}")
            return GrantGuildMembersClaimPermissionsResult.StorageError
        }
    }
}
