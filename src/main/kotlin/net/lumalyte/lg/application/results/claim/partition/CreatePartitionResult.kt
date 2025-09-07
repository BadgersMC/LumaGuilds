package net.lumalyte.lg.application.results.claim.partition

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition

sealed class CreatePartitionResult {
    data class Success(val claim: Claim, val partition: Partition): CreatePartitionResult()
    data class InsufficientBlocks(val requiredExtraBlocks: Int): CreatePartitionResult()
    data class TooSmall(val minimumSize: Int): CreatePartitionResult()
    object Overlaps: CreatePartitionResult()
    object TooClose: CreatePartitionResult()
    object Disconnected: CreatePartitionResult()
    object StorageError: CreatePartitionResult()
}
