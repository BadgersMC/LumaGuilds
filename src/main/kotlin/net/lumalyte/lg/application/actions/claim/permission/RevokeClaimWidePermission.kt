package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.permission.RevokeClaimWidePermissionResult
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class RevokeClaimWidePermission(private val claimPermissionRepository: ClaimPermissionRepository,
                                private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID, permission: ClaimPermission): RevokeClaimWidePermissionResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return RevokeClaimWidePermissionResult.ClaimNotFound

        // Remove the permission for the player in the claim
        try {
            return when (claimPermissionRepository.remove(claimId, permission)) {
                true -> RevokeClaimWidePermissionResult.Success
                false -> RevokeClaimWidePermissionResult.DoesNotExist
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database")
            return RevokeClaimWidePermissionResult.StorageError
        }
    }
}
