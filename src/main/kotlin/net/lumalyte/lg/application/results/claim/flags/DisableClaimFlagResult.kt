package net.lumalyte.lg.application.results.claim.flags

sealed class DisableClaimFlagResult {
    object Success : DisableClaimFlagResult()
    object ClaimNotFound : DisableClaimFlagResult()
    object DoesNotExist : DisableClaimFlagResult()
    object StorageError: DisableClaimFlagResult()
}
