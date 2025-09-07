package net.lumalyte.lg.application.results.claim.anchor

import net.lumalyte.lg.domain.entities.Claim

sealed class GetClaimAnchorAtPositionResult {
    data class Success(val claim: Claim) : GetClaimAnchorAtPositionResult()
    object NoClaimAnchorFound: GetClaimAnchorAtPositionResult()
    object StorageError: GetClaimAnchorAtPositionResult()
}
