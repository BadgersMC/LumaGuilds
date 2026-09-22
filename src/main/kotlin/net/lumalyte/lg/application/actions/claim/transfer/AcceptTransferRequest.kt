package net.lumalyte.lg.application.actions.claim.transfer

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.results.claim.transfer.AcceptTransferRequestResult
import net.lumalyte.lg.application.services.PlayerMetadataService
import java.time.Instant
import java.util.UUID

class AcceptTransferRequest(
    private val claimRepository: ClaimRepository,
    private val playerMetadataService: PlayerMetadataService,
    private val partitionRepository: PartitionRepository,
    private val transferRequests: ClaimTransferRequestRepository,
) {
    fun execute(claimId: UUID, playerId: UUID, newName: String): AcceptTransferRequestResult {
        val claim = claimRepository.getById(claimId)
            ?: return AcceptTransferRequestResult.ClaimNotFound
        if (claim.playerId == playerId) return AcceptTransferRequestResult.PlayerOwnsClaim

        try {
            if (!transferRequests.hasActive(claimId, playerId, Instant.now().epochSecond)) {
                return AcceptTransferRequestResult.NoActiveTransferRequest
            }
        } catch (_: DatabaseOperationException) {
            return AcceptTransferRequestResult.StorageError
        }

        val playerClaimLimit = playerMetadataService.getPlayerClaimLimit(playerId)
        val playerClaims = claimRepository.getByPlayer(playerId)
        if (playerClaims.size >= playerClaimLimit) {
            return AcceptTransferRequestResult.ClaimLimitExceeded
        }

        val playerBlockLimit = playerMetadataService.getPlayerClaimBlockLimit(playerId)
        val playerBlockCount = playerClaims.flatMap { playerClaim ->
            partitionRepository.getByClaim(playerClaim.id)
        }.sumOf { it.getBlockCount() }
        val claimBlockCount = partitionRepository.getByClaim(claim.id).sumOf { it.getBlockCount() }
        if (playerBlockCount + claimBlockCount > playerBlockLimit) {
            return AcceptTransferRequestResult.BlockLimitExceeded
        }

        if (claimRepository.getByName(playerId, newName) != null) {
            return AcceptTransferRequestResult.NameAlreadyExists
        }

        return try {
            // Revoke every outstanding offer before ownership can change. If the claim
            // write then fails the owner can simply send a fresh offer; the inverse
            // ordering could leave a stale offer able to transfer the new owner's claim.
            if (!transferRequests.clearClaim(claimId)) {
                return AcceptTransferRequestResult.StorageError
            }
            val updatedClaim = claim.copy(
                playerId = playerId,
                name = newName,
            )
            if (!claimRepository.update(updatedClaim)) {
                return AcceptTransferRequestResult.StorageError
            }
            AcceptTransferRequestResult.Success
        } catch (_: DatabaseOperationException) {
            AcceptTransferRequestResult.StorageError
        }
    }
}
