package net.lumalyte.lg.application.actions.claim.transfer

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.application.results.claim.transfer.DoesPlayerHaveTransferRequestResult
import java.time.Instant
import java.util.UUID

class DoesPlayerHaveTransferRequest(
    private val claimRepository: ClaimRepository,
    private val transferRequests: ClaimTransferRequestRepository,
) {
    fun execute(claimId: UUID, playerId: UUID): DoesPlayerHaveTransferRequestResult {
        claimRepository.getById(claimId)
            ?: return DoesPlayerHaveTransferRequestResult.ClaimNotFound

        return try {
            DoesPlayerHaveTransferRequestResult.Success(
                transferRequests.hasActive(claimId, playerId, Instant.now().epochSecond)
            )
        } catch (_: DatabaseOperationException) {
            DoesPlayerHaveTransferRequestResult.StorageError
        }
    }
}
