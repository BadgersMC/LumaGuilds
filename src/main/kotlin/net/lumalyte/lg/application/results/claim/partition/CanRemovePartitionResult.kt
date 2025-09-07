package net.lumalyte.lg.application.results.claim.partition

sealed class CanRemovePartitionResult {
    object Success: CanRemovePartitionResult()
    object Disconnected: CanRemovePartitionResult()
    object ExposedClaimAnchor: CanRemovePartitionResult()
    object StorageError: CanRemovePartitionResult()
}
