package net.lumalyte.lg.application.actions.player

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.player.RegisterClaimMenuOpeningResult
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class RegisterClaimMenuOpening(private val playerStateRepository: PlayerStateRepository,
                               private val claimRepository: ClaimRepository) {
    fun execute(playerId: UUID, claimId: UUID): RegisterClaimMenuOpeningResult {
        // Check if claim exists
        claimRepository.getById(claimId) ?: return RegisterClaimMenuOpeningResult.ClaimNotFound

        // Get or create player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Set currently open claim menu
        playerState.isInClaimMenu = claimId
        playerStateRepository.update(playerState)
        return RegisterClaimMenuOpeningResult.Success
    }
}
