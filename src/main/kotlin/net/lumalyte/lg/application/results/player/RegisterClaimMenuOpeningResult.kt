package net.lumalyte.lg.application.results.player

sealed class RegisterClaimMenuOpeningResult {
    object Success: RegisterClaimMenuOpeningResult()
    object ClaimNotFound: RegisterClaimMenuOpeningResult()
}
