package net.lumalyte.lg.application.actions.claim.anchor

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.claim.anchor.GetClaimAnchorAtPositionResult
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

class GetClaimAnchorAtPosition(private val claimRepository: ClaimRepository) {
    fun execute(position3D: Position3D, worldId: UUID): GetClaimAnchorAtPositionResult {
        val claim = claimRepository.getByPosition(position3D, worldId) ?: return GetClaimAnchorAtPositionResult.NoClaimAnchorFound
        return GetClaimAnchorAtPositionResult.Success(claim)
    }
}
