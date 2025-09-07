package net.lumalyte.lg.application.results.claim.permission

sealed class RevokeClaimWidePermissionResult {
    object Success : RevokeClaimWidePermissionResult()
    object ClaimNotFound : RevokeClaimWidePermissionResult()
    object DoesNotExist : RevokeClaimWidePermissionResult()
    object StorageError: RevokeClaimWidePermissionResult()
}
