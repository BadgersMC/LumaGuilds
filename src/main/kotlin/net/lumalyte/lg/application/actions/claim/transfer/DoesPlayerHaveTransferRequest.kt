package net.lumalyte.lg.application.actions.claim.transfer

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.transfer.DoesPlayerHaveTransferRequestResult
import java.util.UUID

class DoesPlayerHaveTransferRequest(private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID, playerId: UUID): DoesPlayerHaveTransferRequestResult {
        val claim = claimRepository.getById(claimId) ?: return DoesPlayerHaveTransferRequestResult.ClaimNotFound
        if (playerId in claim.transferRequests.keys) return DoesPlayerHaveTransferRequestResult.Success(true)
        return DoesPlayerHaveTransferRequestResult.Success(false)
    }
}
