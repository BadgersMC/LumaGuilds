package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.permission.GrantClaimWidePermissionResult
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

class GrantClaimWidePermission(private val claimPermissionRepository: ClaimPermissionRepository,
                               private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID, permission: ClaimPermission): GrantClaimWidePermissionResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return GrantClaimWidePermissionResult.ClaimNotFound

        // Add the permission for the player in the claim
        try {
            return when (claimPermissionRepository.add(claimId, permission)) {
                true -> GrantClaimWidePermissionResult.Success
                false -> GrantClaimWidePermissionResult.AlreadyExists
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database")
            return GrantClaimWidePermissionResult.StorageError
        }
    }
}
