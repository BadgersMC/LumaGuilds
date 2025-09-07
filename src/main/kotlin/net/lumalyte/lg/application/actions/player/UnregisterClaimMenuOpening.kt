package net.lumalyte.lg.application.actions.player

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.player.UnregisterClaimMenuOpeningResult
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class UnregisterClaimMenuOpening(private val playerStateRepository: PlayerStateRepository) {
    fun execute(playerId: UUID): UnregisterClaimMenuOpeningResult {
        // Get or create player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // No claim is registered to unregister
        if (playerState.isInClaimMenu == null) return UnregisterClaimMenuOpeningResult.NotRegistered

        // Unset currently open claim menu
        playerState.isInClaimMenu = null
        playerStateRepository.update(playerState)
        return UnregisterClaimMenuOpeningResult.Success
    }
}
