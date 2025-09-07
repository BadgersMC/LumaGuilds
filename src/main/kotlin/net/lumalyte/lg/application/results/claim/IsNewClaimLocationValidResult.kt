package net.lumalyte.lg.application.results.claim

sealed class IsNewClaimLocationValidResult {
    object Valid: IsNewClaimLocationValidResult()
    object Overlap: IsNewClaimLocationValidResult()
    object TooClose: IsNewClaimLocationValidResult()
    object TooCloseToWorldBorder: IsNewClaimLocationValidResult()
    object StorageError: IsNewClaimLocationValidResult()
}
