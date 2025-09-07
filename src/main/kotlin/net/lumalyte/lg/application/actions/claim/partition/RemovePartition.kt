package net.lumalyte.lg.application.actions.claim.partition

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.results.claim.partition.CanRemovePartitionResult
import net.lumalyte.lg.application.results.claim.partition.RemovePartitionResult
import java.util.UUID

/**
 * Action for removing a specific partition from a claim.
 *
 * @property partitionRepository Repository for managing partitions.
 */
class RemovePartition(private val partitionRepository: PartitionRepository,
                      private val canRemovePartition: CanRemovePartition) {

    /**
     * Removes the specified partition using its given [partitionId].
     *
     * @param partitionId The [java.util.UUID] of the claim to which the flag should be added.
     * @return An [RemovePartitionResult] indicating the outcome of the flag addition operation.
     */
    fun execute(partitionId: UUID): RemovePartitionResult {
        // Check if the removal will result in disconnection
         when (canRemovePartition.execute(partitionId)) {
             CanRemovePartitionResult.Success -> Unit
             CanRemovePartitionResult.StorageError -> return RemovePartitionResult.StorageError
             CanRemovePartitionResult.Disconnected -> return RemovePartitionResult.Disconnected
             CanRemovePartitionResult.ExposedClaimAnchor -> return RemovePartitionResult.ExposedClaimAnchor
         }

        // Remove the partition from the claim
        try {
            return when (partitionRepository.remove(partitionId)) {
                true -> RemovePartitionResult.Success
                false -> RemovePartitionResult.DoesNotExist
            }
        } catch (_: DatabaseOperationException) {
            println("Error has occurred trying to save to the database")
            return RemovePartitionResult.StorageError
        }
    }
}
