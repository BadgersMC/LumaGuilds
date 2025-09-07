package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.player.visualisation.IsPlayerVisualisingResult
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class IsPlayerVisualising(private val playerStateRepository: PlayerStateRepository) {
    fun execute(playerId: UUID): IsPlayerVisualisingResult {
        // Get or create player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        if (playerState.isVisualisingClaims) {
            return IsPlayerVisualisingResult.Success(true)
        }
        return IsPlayerVisualisingResult.Success(false)
    }
}
