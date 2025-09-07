package net.lumalyte.lg.application.actions.claim

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.values.Position
import java.util.UUID

class GetClaimAtPosition(private val claimRepository: ClaimRepository,
                         private val partitionRepository: PartitionRepository) {
    fun execute(worldId: UUID, position: Position): GetClaimAtPositionResult {
        val partitions = partitionRepository.getByPosition(position)
        val worldPartition = filterByWorld(worldId, partitions) ?: return GetClaimAtPositionResult.NoClaimFound
        val claim = claimRepository.getById(worldPartition.claimId) ?: return GetClaimAtPositionResult.NoClaimFound
        return GetClaimAtPositionResult.Success(claim)
    }

    private fun filterByWorld(worldId: UUID, inputPartitions: Set<Partition>): Partition? {
        for (partition in inputPartitions) {
            val claimPartition = claimRepository.getById(partition.claimId) ?: continue
            if (claimPartition.worldId == worldId) {
                return partition
            }
        }
        return null
    }
}
