package net.lumalyte.lg.application.results.claim.permission

sealed class GrantAllClaimWidePermissionsResult {
    object Success : GrantAllClaimWidePermissionsResult()
    object ClaimWideNotFound : GrantAllClaimWidePermissionsResult()
    object AllAlreadyGrantedWide : GrantAllClaimWidePermissionsResult()
    object StorageError: GrantAllClaimWidePermissionsResult()
}
