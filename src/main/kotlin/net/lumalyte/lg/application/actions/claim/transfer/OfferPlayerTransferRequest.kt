package net.lumalyte.lg.application.actions.claim.transfer

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.application.results.claim.transfer.OfferPlayerTransferRequestResult
import java.time.Instant
import java.util.UUID

class OfferPlayerTransferRequest(
    private val claimRepository: ClaimRepository,
    private val transferRequests: ClaimTransferRequestRepository,
) {
    fun execute(claimId: UUID, playerId: UUID): OfferPlayerTransferRequestResult {
        claimRepository.getById(claimId)
            ?: return OfferPlayerTransferRequestResult.ClaimNotFound

        return try {
            val now = Instant.now().epochSecond
            if (transferRequests.hasActive(claimId, playerId, now)) {
                return OfferPlayerTransferRequestResult.RequestAlreadyPending
            }
            if (transferRequests.offer(claimId, playerId, now + REQUEST_TTL_SECONDS)) {
                OfferPlayerTransferRequestResult.Success
            } else {
                OfferPlayerTransferRequestResult.StorageError
            }
        } catch (_: DatabaseOperationException) {
            OfferPlayerTransferRequestResult.StorageError
        }
    }

    private companion object {
        const val REQUEST_TTL_SECONDS = 5 * 60L
    }
}
