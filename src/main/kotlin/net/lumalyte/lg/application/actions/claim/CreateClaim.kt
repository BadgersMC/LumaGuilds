package net.lumalyte.lg.application.actions.claim

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.application.services.WorldManipulationService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

class CreateClaim(private val claimRepository: ClaimRepository, private val partitionRepository: PartitionRepository,
                  private val playerMetadataService: PlayerMetadataService,
                  private val worldManipulationService: WorldManipulationService,
                  private val guildService: GuildService,
                  private val config: MainConfig
) {

    fun execute(playerId: UUID, name: String, position3D: Position3D, worldId: UUID): net.lumalyte.lg.application.results.claim.CreateClaimResult {
        // All claims start as personal claims - no automatic guild ownership
        val guildId: UUID? = null

        // Get claims for individual player (all claims are personal by default)
        val existingClaims = claimRepository.getByPlayer(playerId)

        // Check individual claim limits
        val claimLimit = playerMetadataService.getPlayerClaimLimit(playerId)

        if (existingClaims.count() >= claimLimit) {
            return net.lumalyte.lg.application.results.claim.CreateClaimResult.LimitExceeded
        }

        // Check if input name is blank
        if (name.isBlank()) {
            return net.lumalyte.lg.application.results.claim.CreateClaimResult.NameCannotBeBlank
        }

        // Check if name already exists for this player
        val existingClaim = claimRepository.getByName(playerId, name)

        if (existingClaim != null) {
            return net.lumalyte.lg.application.results.claim.CreateClaimResult.NameAlreadyExists
        }

        // Generates the partition area based on config
        val areaSize = config.initialClaimSize
        val offsetMin = (areaSize - 1) / 2
        val offsetMax = areaSize / 2
        val area = Area(
            Position2D(position3D.x - offsetMin, position3D.z - offsetMin),
            Position2D(position3D.x + offsetMax, position3D.z + offsetMax)
        )

        // Validate area is within world border
        if (!worldManipulationService.isInsideWorldBorder(worldId, area)) {
            return net.lumalyte.lg.application.results.claim.CreateClaimResult.TooCloseToWorldBorder
        }

        // Creates new claim and partition with guild ownership if applicable
        val newClaim = Claim(worldId, playerId, guildId, position3D, name)
        val partition = Partition(newClaim.id, area)
        claimRepository.add(newClaim)
        partitionRepository.add(partition)
        return net.lumalyte.lg.application.results.claim.CreateClaimResult.Success(newClaim)
    }
}
