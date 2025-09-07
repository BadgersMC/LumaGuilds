package net.lumalyte.lg.application.results.player

sealed class DoesPlayerHaveClaimOverrideResult {
    data class Success(val hasOverride: Boolean): DoesPlayerHaveClaimOverrideResult()
    object StorageError: DoesPlayerHaveClaimOverrideResult()
}
