package net.lumalyte.lg.application.results.claim.anchor

sealed class MoveClaimAnchorResult {
    object Success: MoveClaimAnchorResult()
    object NoPermission: MoveClaimAnchorResult()
    object InvalidPosition: MoveClaimAnchorResult()
    object StorageError: MoveClaimAnchorResult()
}
