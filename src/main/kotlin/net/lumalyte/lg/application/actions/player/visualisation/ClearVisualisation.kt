package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID
import kotlin.collections.flatten

class ClearVisualisation(
    private val playerStateRepository: PlayerStateRepository,
    private val visualisationService: VisualisationService
) {
    /**
     * Clears the claim visualisation for the target player
     */
    fun execute(playerId: UUID) {
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Get all the blocks to unvisualise, including partition-based and currently selected
        val claimBlocksToUnvisualise = playerState.visualisedClaims.values.flatten().toMutableSet()
        claimBlocksToUnvisualise.addAll(playerState.visualisedPartitions.values.flatMap { innerMap -> innerMap.values }
            .flatten())

        // Unvisualise
        visualisationService.clear(playerId, claimBlocksToUnvisualise)

        // Nullify visualizations in the player state
        playerState.visualisedClaims.clear()
        playerState.visualisedPartitions.clear()
        playerState.isVisualisingClaims = false
        playerStateRepository.update(playerState)
        return
    }
}
