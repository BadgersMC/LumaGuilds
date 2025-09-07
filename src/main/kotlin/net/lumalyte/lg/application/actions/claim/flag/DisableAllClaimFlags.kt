package net.lumalyte.lg.application.actions.claim.flag

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.flags.DisableAllClaimFlagsResult
import net.lumalyte.lg.domain.values.Flag
import java.util.UUID

/**
 * Action for disabling all flags for a claim.
 *
 * @property flagRepository Repository for managing claim flags.
 * @property claimRepository Repository for managing claims.
 */
class DisableAllClaimFlags(private val flagRepository: ClaimFlagRepository,
                          private val claimRepository: ClaimRepository) {

    /**
     * Removes all available flags from the claim with the given [claimId].
     *
     * @param claimId The [UUID] of the claim to which the flag should be added.
     * @return An [DisableAllClaimFlagsResult] indicating the outcome of the flag removal operation.
     */
    fun execute(claimId: UUID): DisableAllClaimFlagsResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return DisableAllClaimFlagsResult.ClaimNotFound

        // Remove all flags to the claim
        var anyFlagEnabled = false
        try {
            val allFlags = Flag.entries
            for (flag in allFlags) {
                if (flagRepository.remove(claimId, flag)) anyFlagEnabled = true
            }

            // Return success if at least one flag was disabled
            return if (anyFlagEnabled) {
                DisableAllClaimFlagsResult.Success
            } else {
                DisableAllClaimFlagsResult.AllAlreadyDisabled
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database: ${error.message}")
            return DisableAllClaimFlagsResult.StorageError
        }
    }
}
