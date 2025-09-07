package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class ClearSelectionVisualisation(
    private val playerStateRepository: PlayerStateRepository,
    private val visualisationService: VisualisationService
) {
    fun execute(playerId: UUID) {
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Visualise the block if currently visualised
        val selectedBlock = playerState.selectedBlock
        if (selectedBlock == null) return
        visualisationService.clear(playerId, setOf(selectedBlock))

        // Set visualization in the player state
        playerState.selectedBlock = null
        playerStateRepository.update(playerState)
    }
}
