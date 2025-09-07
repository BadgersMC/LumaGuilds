package net.lumalyte.lg.application.actions.claim.flag

import net.lumalyte.lg.application.results.claim.flags.DisableClaimFlagResult
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.domain.values.Flag
import java.util.UUID

/**
 * Action for removing a specific flag from a claim.
 *
 * @property flagRepository Repository for managing claim flags.
 * @property claimRepository Repository for managing claims.
 */
class DisableClaimFlag(private val flagRepository: ClaimFlagRepository, private val claimRepository: ClaimRepository) {

    /**
     * Removes the specified [flag] from the claim with the given [claimId].
     *
     * @param flag The [Flag] to be added to the claim.
     * @param claimId The [UUID] of the claim to which the flag should be added.
     * @return An [DisableClaimFlagResult] indicating the outcome of the flag addition operation.
     */
    fun execute(flag: Flag, claimId: UUID): DisableClaimFlagResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return DisableClaimFlagResult.ClaimNotFound

        // Add the flag to the claim
        try {
            return when (flagRepository.remove(claimId, flag)) {
                true -> DisableClaimFlagResult.Success
                false -> DisableClaimFlagResult.DoesNotExist
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database")
            return DisableClaimFlagResult.StorageError
        }
    }
}
