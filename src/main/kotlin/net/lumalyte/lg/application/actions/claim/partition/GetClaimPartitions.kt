package net.lumalyte.lg.application.actions.claim.partition

import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.domain.entities.Partition
import java.util.UUID

class GetClaimPartitions(private val partitionRepository: PartitionRepository) {
    fun execute(claimId: UUID): List<Partition> {
        return partitionRepository.getByClaim(claimId).toList().sortedBy { it.getBlockCount() }
    }
}
