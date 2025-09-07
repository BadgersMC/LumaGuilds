package net.lumalyte.lg.application.actions.claim.transfer

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.transfer.OfferPlayerTransferRequestResult
import java.util.UUID

class OfferPlayerTransferRequest(private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID, playerId: UUID): OfferPlayerTransferRequestResult {
        val claim = claimRepository.getById(claimId) ?: return OfferPlayerTransferRequestResult.ClaimNotFound
        if (playerId in claim.transferRequests.keys) return OfferPlayerTransferRequestResult.RequestAlreadyPending

        val currentTimestamp: Int = (System.currentTimeMillis() / 1000).toInt()
        val requestExpireTimestamp = currentTimestamp + (5 * 60)
        claim.transferRequests.put(playerId, requestExpireTimestamp)
        return OfferPlayerTransferRequestResult.Success
    }
}
