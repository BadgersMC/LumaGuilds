package net.lumalyte.lg.application.results.claim

import net.lumalyte.lg.domain.entities.Claim

sealed class GetClaimAtPositionResult {
    data class Success(val claim: Claim) : GetClaimAtPositionResult()
    object NoClaimFound: GetClaimAtPositionResult()
    object StorageError: GetClaimAtPositionResult()
}
