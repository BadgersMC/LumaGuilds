package net.lumalyte.lg.application.results.claim.partition

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition

sealed class ResizePartitionResult {
    data class Success(val claim: Claim, val partition: Partition, val remainingBlocks: Int): ResizePartitionResult()
    data class InsufficientBlocks(val requiredExtraBlocks: Int): ResizePartitionResult()
    data class TooSmall(val minimumSize: Int): ResizePartitionResult()
    object Overlaps: ResizePartitionResult()
    object TooClose: ResizePartitionResult()
    object Disconnected: ResizePartitionResult()
    object ExposedClaimAnchor: ResizePartitionResult()
    object StorageError: ResizePartitionResult()
}
