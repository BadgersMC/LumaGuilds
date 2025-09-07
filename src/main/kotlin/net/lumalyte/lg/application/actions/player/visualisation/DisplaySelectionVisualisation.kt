package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.domain.entities.PlayerState
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

class DisplaySelectionVisualisation(
    private val playerStateRepository: PlayerStateRepository,
    private val visualisationService: VisualisationService
) {
    private val selectionBlock = "LIME_GLAZED_TERRACOTTA"
    private val selectionCarpet = "LIME_CARPET"

    fun execute(playerId: UUID, position: Position3D) {
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Visualise the block
        visualisationService.displaySelected(playerId, position, selectionBlock, selectionCarpet)

        // Set visualization in the player state
        playerState.selectedBlock = position
        playerStateRepository.update(playerState)
    }
}
