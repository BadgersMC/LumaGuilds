package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.permission.RevokeAllClaimWidePermissionsResult
import net.lumalyte.lg.application.results.claim.permission.RevokeAllPlayerClaimPermissionsResult
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class RevokeAllClaimWidePermissions(private val claimRepository: ClaimRepository,
                                    private val claimPermissionRepository: ClaimPermissionRepository) {
    /**
     * Removes all available permissions to the claim with the given [claimId].
     *
     * @param claimId The [UUID] of the claim to which the flag should be added.
     * @return An [RevokeAllPlayerClaimPermissionsResult] indicating the outcome of the flag addition operation.
     */
    fun execute(claimId: UUID): RevokeAllClaimWidePermissionsResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return RevokeAllClaimWidePermissionsResult.ClaimNotFound

        // Remove all permissions from the player
        var anyPermissionDisabled = false
        try {
            val allPermissions = ClaimPermission.entries
            for (permission in allPermissions) {
                if (claimPermissionRepository.remove(claimId, permission)) anyPermissionDisabled = true
            }

            // Return success if at least one permission was revoked
            return if (anyPermissionDisabled) {
                RevokeAllClaimWidePermissionsResult.Success
            } else {
                RevokeAllClaimWidePermissionsResult.AllAlreadyRevoked
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database: ${error.message}")
            return RevokeAllClaimWidePermissionsResult.StorageError
        }
    }
}
