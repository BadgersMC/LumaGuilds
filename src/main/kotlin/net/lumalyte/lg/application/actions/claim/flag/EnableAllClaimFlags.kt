package net.lumalyte.lg.application.actions.claim.flag

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.flags.EnableAllClaimFlagsResult
import net.lumalyte.lg.domain.values.Flag
import java.util.UUID

/**
 * Action for adding a specific flag to a claim.
 *
 * @property flagRepository Repository for managing claim flags.
 * @property claimRepository Repository for managing claims.
 */
class EnableAllClaimFlags(private val flagRepository: ClaimFlagRepository,
                         private val claimRepository: ClaimRepository) {

    /**
     * Adds all available flags to the claim with the given [claimId].
     *
     * @param claimId The [UUID] of the claim to which the flag should be added.
     * @return An [EnableAllClaimFlagsResult] indicating the outcome of the flag addition operation.
     */
    fun execute(claimId: UUID): EnableAllClaimFlagsResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return EnableAllClaimFlagsResult.ClaimNotFound

        // Add all flags to the claim
        var anyFlagEnabled = false
        try {
            val allFlags = Flag.entries
            for (flag in allFlags) {
                if (flagRepository.add(claimId, flag)) anyFlagEnabled = true
            }

            // Return success if at least one flag was enabled
            return if (anyFlagEnabled) {
                EnableAllClaimFlagsResult.Success
            } else {
                EnableAllClaimFlagsResult.AllAlreadyEnabled
            }
        } catch (error: DatabaseOperationException) {
            println("Error has occurred trying to save to the database: ${error.message}")
            return EnableAllClaimFlagsResult.StorageError
        }
    }
}
