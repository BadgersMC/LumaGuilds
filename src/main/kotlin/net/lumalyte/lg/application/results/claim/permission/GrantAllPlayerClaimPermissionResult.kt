package net.lumalyte.lg.application.results.claim.permission

sealed class GrantAllPlayerClaimPermissionsResult {
    object Success : GrantAllPlayerClaimPermissionsResult()
    object ClaimNotFound : GrantAllPlayerClaimPermissionsResult()
    object AllAlreadyGranted : GrantAllPlayerClaimPermissionsResult()
    object StorageError: GrantAllPlayerClaimPermissionsResult()
}
