package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.player.visualisation.GetVisualisedClaimBlocksResult
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class GetVisualisedClaimBlocks(private val claimRepository: ClaimRepository,
                               private val playerStateRepository: PlayerStateRepository) {
    fun execute(playerId: UUID, claimId: UUID): GetVisualisedClaimBlocksResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return GetVisualisedClaimBlocksResult.ClaimNotFound

        // Get or create player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Toggle override and persist to storage
        val blockPositions = playerState.visualisedClaims[claimId]
        if (blockPositions != null) {
            return GetVisualisedClaimBlocksResult.Success(blockPositions.toSet())
        }
        return GetVisualisedClaimBlocksResult.NotVisualising
    }
}
