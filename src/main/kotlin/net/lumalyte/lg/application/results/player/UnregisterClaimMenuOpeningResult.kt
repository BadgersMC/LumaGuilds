package net.lumalyte.lg.application.results.player

sealed class UnregisterClaimMenuOpeningResult {
    object Success: UnregisterClaimMenuOpeningResult()
    object NotRegistered: UnregisterClaimMenuOpeningResult()
    object ClaimNotFound: UnregisterClaimMenuOpeningResult()
}
