package net.lumalyte.lg.application.actions.claim.anchor

import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.results.claim.anchor.MoveClaimAnchorResult
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.services.WorldManipulationService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

class MoveClaimAnchor(private val claimRepository: ClaimRepository,
                      private val worldManipulationService: WorldManipulationService,
                      private val getClaimAtPosition: GetClaimAtPosition,
                      private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride
) {
    fun execute(claimId: UUID, playerId: UUID, newWorldId: UUID, newPosition: Position3D): MoveClaimAnchorResult {
        // Get the claim that is being moved
        val existingClaim = claimRepository.getById(claimId) ?: return MoveClaimAnchorResult.StorageError

        // The destination may be empty or already inside this claim, but it may not
        // overlap another claim.
        when (val result = getClaimAtPosition.execute(newWorldId, newPosition)) {
            is GetClaimAtPositionResult.Success -> {
                if (result.claim.id != claimId) return MoveClaimAnchorResult.InvalidPosition
            }
            is GetClaimAtPositionResult.NoClaimFound -> Unit
            is GetClaimAtPositionResult.StorageError -> return MoveClaimAnchorResult.StorageError
        }

        // Get player's claim override
        val result = doesPlayerHaveClaimOverride.execute(playerId)
        val claimOverride = when (result) {
            is DoesPlayerHaveClaimOverrideResult.StorageError -> false
            is DoesPlayerHaveClaimOverrideResult.Success -> result.hasOverride
        }

        // Check if the player moving the claim bell is the owner of the claim
        if (existingClaim.playerId != playerId && !claimOverride) {
            return MoveClaimAnchorResult.NoPermission
        }

        // Persist the authoritative location before removing the old anchor block.
        val newClaim = existingClaim.copy(worldId = newWorldId, position = newPosition)
        if (!claimRepository.update(newClaim)) {
            return MoveClaimAnchorResult.StorageError
        }
        worldManipulationService.breakWithoutItemDrop(existingClaim.worldId, existingClaim.position)
        return MoveClaimAnchorResult.Success
    }
}
