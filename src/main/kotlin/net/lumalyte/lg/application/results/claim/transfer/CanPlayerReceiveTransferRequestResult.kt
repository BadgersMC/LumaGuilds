package net.lumalyte.lg.application.results.claim.transfer

sealed class CanPlayerReceiveTransferRequestResult {
    object Success: CanPlayerReceiveTransferRequestResult()
    object ClaimLimitExceeded: CanPlayerReceiveTransferRequestResult()
    object BlockLimitExceeded: CanPlayerReceiveTransferRequestResult()
    object PlayerOwnsClaim: CanPlayerReceiveTransferRequestResult()
    object ClaimNotFound: CanPlayerReceiveTransferRequestResult()
    object StorageError: CanPlayerReceiveTransferRequestResult()
}
