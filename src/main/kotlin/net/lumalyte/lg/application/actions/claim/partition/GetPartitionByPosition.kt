package net.lumalyte.lg.application.actions.claim.partition

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.values.Position
import java.util.UUID

class GetPartitionByPosition(private val partitionRepository: PartitionRepository,
                             private val claimRepository: ClaimRepository
) {
    fun execute(position: Position, worldId: UUID): Partition? {
        val partitions = partitionRepository.getByPosition(position)

        for (partition in partitions) {
            val claim = claimRepository.getById(partition.claimId) ?: continue
            if (claim.worldId == worldId) return partition
        }
        return null
    }
}
