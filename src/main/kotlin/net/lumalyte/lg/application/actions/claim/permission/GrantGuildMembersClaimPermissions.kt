package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.results.claim.permission.GrantGuildMembersClaimPermissionsResult
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class GrantGuildMembersClaimPermissions(private val claimRepository: ClaimRepository,
                                       private val memberService: MemberService,
                                       private val playerAccessRepository: PlayerAccessRepository) {
    /**
     * Grants all available permissions to all members of the guild that owns the claim.
     *
     * @param claimId The UUID of the claim to share with guild members.
     * @param playerId The UUID of the player performing the action (must be claim owner).
     * @return A GrantGuildMembersClaimPermissionsResult indicating the outcome.
     */
    fun execute(claimId: UUID, playerId: UUID): GrantGuildMembersClaimPermissionsResult {
        // Check if claim exists and get it
        val claim = claimRepository.getById(claimId)
            ?: return GrantGuildMembersClaimPermissionsResult.ClaimNotFound

        // Verify player owns the claim
        if (claim.playerId != playerId) {
            return GrantGuildMembersClaimPermissionsResult.NotClaimOwner
        }

        // Check if claim is guild-owned
        val guildId = claim.teamId
            ?: return GrantGuildMembersClaimPermissionsResult.ClaimNotGuildOwned

        // Get all guild members
        val guildMembers = memberService.getGuildMembers(guildId)
        if (guildMembers.isEmpty()) {
            return GrantGuildMembersClaimPermissionsResult.NoGuildMembers
        }

        // Grant permissions to all guild members (excluding the claim owner)
        var grantedCount = 0
        var alreadyHadAccessCount = 0

        try {
            val allPermissions = ClaimPermission.entries

            for (member in guildMembers) {
                // Skip the claim owner
                if (member.playerId == playerId) continue

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
