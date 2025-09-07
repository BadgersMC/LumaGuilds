package net.lumalyte.lg.application.results.claim.metadata

import net.lumalyte.lg.domain.entities.Claim

sealed class UpdateClaimIconResult {
    data class Success(val claim: Claim): UpdateClaimIconResult()
    object NoClaimFound: UpdateClaimIconResult()
    object StorageError: UpdateClaimIconResult()
}
