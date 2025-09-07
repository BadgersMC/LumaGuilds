package net.lumalyte.lg.application.results.claim

import net.lumalyte.lg.domain.entities.Claim

sealed class IsPlayerActionAllowedResult {
    data class Allowed(val claim: Claim): IsPlayerActionAllowedResult()
    data class Denied(val claim: Claim): IsPlayerActionAllowedResult()
    object NoClaimFound: IsPlayerActionAllowedResult()
    object NoAssociatedPermission: IsPlayerActionAllowedResult()
    object StorageError: IsPlayerActionAllowedResult()
}
