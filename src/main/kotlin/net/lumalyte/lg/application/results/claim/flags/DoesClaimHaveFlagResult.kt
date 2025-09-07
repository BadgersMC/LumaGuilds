package net.lumalyte.lg.application.results.claim.flags

sealed class DoesClaimHaveFlagResult {
    data class Success(val hasFlag: Boolean) : DoesClaimHaveFlagResult()
    object ClaimNotFound : DoesClaimHaveFlagResult()
    object StorageError: DoesClaimHaveFlagResult()
}
