package net.lumalyte.lg.application.actions.claim.metadata

import net.lumalyte.lg.application.persistence.PartitionRepository
import java.util.UUID

class GetClaimBlockCount(private val partitionRepository: PartitionRepository) {
    fun execute(claimId: UUID): Int {
        val partitions = partitionRepository.getByClaim(claimId)

        // No partitions found, 0 blocks
        if (partitions.isEmpty()) {
            return 0
        }

        // Addition for all partition block counts
        var totalBlocks = 0
        for (partition in partitions) {
            totalBlocks += partition.getBlockCount()
        }
        return totalBlocks
    }
}
