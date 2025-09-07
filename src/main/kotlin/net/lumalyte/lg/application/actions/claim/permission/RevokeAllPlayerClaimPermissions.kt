package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.results.claim.permission.RevokeAllPlayerClaimPermissionsResult
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class RevokeAllPlayerClaimPermissions(private val claimRepository: ClaimRepository,
                                      private val playerAccessRepository: PlayerAccessRepository) {
    /**
     * Removes all available flags to the claim with the given [claimId].
     *
     * @param claimId The [UUID] of the claim to which the flag should be added.
     * @return An [RevokeAllPlayerClaimPermissionsResult] indicating the outcome of the flag addition operation.
     */
    fun execute(claimId: UUID, playerId: UUID): RevokeAllPlayerClaimPermissionsResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return RevokeAllPlayerClaimPermissionsResult.ClaimNotFound

        // Remove all permissions from the player
        var anyPermissionDisabled = false
        try {
            val allPermissions = ClaimPermission.entries
            for (permission in allPermissions) {
                if (playerAccessRepository.remove(claimId, playerId, permission)) anyPermissionDisabled = true
            }

            // Return success if at least one permission was revoked
            return if (anyPermissionDisabled) {
                RevokeAllPlayerClaimPermissionsResult.Success
            } else {
                RevokeAllPlayerClaimPermissionsResult.AllAlreadyRevoked
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database: ${error.message}")
            return RevokeAllPlayerClaimPermissionsResult.StorageError
        }
    }
}
