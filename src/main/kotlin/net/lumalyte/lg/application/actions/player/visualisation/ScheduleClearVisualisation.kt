package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.player.visualisation.ScheduleClearVisualisationResult
import net.lumalyte.lg.application.services.scheduling.SchedulerService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class ScheduleClearVisualisation(private val playerStateRepository: PlayerStateRepository,
                                 private val schedulerService: SchedulerService,
                                 private val clearVisualisation: ClearVisualisation,
                                 private val clearSelectionVisualisation: ClearSelectionVisualisation,
                                 private val config: MainConfig) {
    fun execute(playerId: UUID): ScheduleClearVisualisationResult {
        // Get or create player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Cancel any existing hide timer first
        playerState.scheduledVisualiserHide?.cancel()
        playerState.scheduledVisualiserHide = null

        // Only schedule a new timer if the player currently has a visualization active
        if (playerState.isVisualisingClaims) {
            playerState.isVisualisingClaims = false
            val delayTicks = (20L * config.visualiserHideDelayPeriod).toLong()
            val task = schedulerService.schedule(delayTicks) {
                clearVisualisation.execute(playerId)
                clearSelectionVisualisation.execute(playerId)
            }
            playerState.scheduledVisualiserHide = task
            return ScheduleClearVisualisationResult.Success
        } else {
            return ScheduleClearVisualisationResult.PlayerNotVisualising
        }
    }
}
