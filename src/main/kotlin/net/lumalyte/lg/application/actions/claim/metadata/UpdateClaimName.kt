package net.lumalyte.lg.application.actions.claim.metadata

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimNameResult
import java.util.UUID

class UpdateClaimName(private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID, name: String): UpdateClaimNameResult {
        // Check if claim exists
        val existingClaim = claimRepository.getById(claimId) ?: return UpdateClaimNameResult.ClaimNotFound

        // Check if name already exists in player's list of claims
        if (claimRepository.getByName(existingClaim.playerId, name) != null)
            return UpdateClaimNameResult.NameAlreadyExists

        // Change Name and persist to storage
        val newClaim = existingClaim.copy(name = name)
        try {
            claimRepository.update(newClaim)
            return UpdateClaimNameResult.Success(newClaim)
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database")
            return UpdateClaimNameResult.StorageError
        }
    }
}
