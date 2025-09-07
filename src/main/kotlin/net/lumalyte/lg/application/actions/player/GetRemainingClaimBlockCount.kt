package net.lumalyte.lg.application.actions.player

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.services.PlayerMetadataService
import java.util.UUID

class GetRemainingClaimBlockCount(private val claimRepository: ClaimRepository,
                                  private val partitionRepository: PartitionRepository,
                                  private val playerMetadataService: PlayerMetadataService) {
    fun execute(playerId: UUID): Int {
        val playerBlockLimit = playerMetadataService.getPlayerClaimBlockLimit(playerId)
        val playerBlockCount = claimRepository.getByPlayer(playerId).flatMap { playerClaim ->
            partitionRepository.getByClaim(playerClaim.id)
        }.sumOf { partition ->
            partition.getBlockCount()
        }
        return playerBlockLimit - playerBlockCount
    }
}
