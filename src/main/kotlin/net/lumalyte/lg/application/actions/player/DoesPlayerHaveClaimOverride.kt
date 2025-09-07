package net.lumalyte.lg.application.actions.player

import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class DoesPlayerHaveClaimOverride(private val playerStateRepository: PlayerStateRepository) {
    fun execute(playerId: UUID): DoesPlayerHaveClaimOverrideResult {
        // Get or create player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        return DoesPlayerHaveClaimOverrideResult.Success(playerState.claimOverride)
    }
}
