package net.lumalyte.lg.application.actions.claim.transfer

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.application.results.claim.transfer.WithdrawPlayerTransferRequestResult
import java.time.Instant
import java.util.UUID

class WithdrawPlayerTransferRequest(
    private val claimRepository: ClaimRepository,
    private val transferRequests: ClaimTransferRequestRepository,
) {
    fun execute(claimId: UUID, playerId: UUID): WithdrawPlayerTransferRequestResult {
        claimRepository.getById(claimId)
            ?: return WithdrawPlayerTransferRequestResult.ClaimNotFound

        return try {
            if (!transferRequests.hasActive(claimId, playerId, Instant.now().epochSecond)) {
                return WithdrawPlayerTransferRequestResult.NoPendingRequest
            }
            if (transferRequests.withdraw(claimId, playerId)) {
                WithdrawPlayerTransferRequestResult.Success
            } else {
                WithdrawPlayerTransferRequestResult.StorageError
            }
        } catch (_: DatabaseOperationException) {
            WithdrawPlayerTransferRequestResult.StorageError
        }
    }
}
