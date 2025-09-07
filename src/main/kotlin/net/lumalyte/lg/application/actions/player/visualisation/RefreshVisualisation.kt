package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.PlayerState
import java.util.UUID

class RefreshVisualisation(private val playerStateRepository: PlayerStateRepository,
                           private val claimRepository: ClaimRepository,
                           private val partitionRepository: PartitionRepository,
                           private val visualisationService: VisualisationService,
                           private val guildService: GuildService) {
    fun execute(playerId: UUID, claimId: UUID, partitionId: UUID) {
        // Get or create the player state if it doesn't exist
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
        }

        // Get player's guild information for guild-aware visualization
        val playerGuilds = guildService.getPlayerGuilds(playerId)
        val playerGuildId = playerGuilds.firstOrNull()?.id

        // Always display complete for claims the player doesn't own
        val claim = claimRepository.getById(claimId) ?: return
        if (claim.playerId != playerId) {
            val visualisedPositions = playerState.visualisedClaims[claimId] ?: return
            val partitions = partitionRepository.getByClaim(claim.id)
            val areas = partitions.map { it.area }.toMutableSet()
            
            // Use guild-aware visualization for non-owned claims
            val isGuildOwned = claim.teamId != null
            val claimOwnerId = claim.teamId ?: claim.playerId
            
            val newPositions = visualisationService.displayGuildAware(
                playerId, areas, claimOwnerId, isGuildOwned, playerGuildId,
                "RED_GLAZED_TERRACOTTA", "RED_CARPET", 
                "BLACK_GLAZED_TERRACOTTA", "BLACK_CARPET"
            )
            playerState.visualisedClaims[claimId] = newPositions
            return
        }

        // For player's own claims, change the refresh type based on player's current visualisation mode
        if (playerState.claimToolMode == 1) {
            // Partitioned mode
            val partition = partitionRepository.getById(partitionId) ?: return
            val visualisedPositions = playerState.visualisedPartitions[claim.id]?.get(partitionId) ?: return
            
            if (partition.area.isPositionInArea(claim.position)) {
                // Main partition - use guild-aware visualization
                val isGuildOwned = claim.teamId != null
                val claimOwnerId = claim.teamId ?: claim.playerId
                
                val newPositions = visualisationService.displayGuildAware(
                    playerId, setOf(partition.area), claimOwnerId, isGuildOwned, playerGuildId,
                    "CYAN_GLAZED_TERRACOTTA", "CYAN_CARPET",
                    "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET"
                )
                playerState.visualisedPartitions.computeIfAbsent(claim.id) { mutableMapOf() }[partition.id] = newPositions
            } else {
                // Attached partitions - use guild-aware visualization
                val isGuildOwned = claim.teamId != null
                val claimOwnerId = claim.teamId ?: claim.playerId
                
                val newPositions = visualisationService.displayGuildAware(
                    playerId, setOf(partition.area), claimOwnerId, isGuildOwned, playerGuildId,
                    "LIGHT_GRAY_GLAZED_TERRACOTTA", "LIGHT_GRAY_CARPET",
                    "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET"
                )
                playerState.visualisedPartitions.computeIfAbsent(claim.id) { mutableMapOf() }[partition.id] = newPositions
            }
        }
        else {
            // Display complete - use guild-aware visualization
            val visualisedPositions = playerState.visualisedClaims[claimId] ?: return
            val partitions = partitionRepository.getByClaim(claim.id)
            val areas = partitions.map { it.area }.toMutableSet()
            
            val isGuildOwned = claim.teamId != null
            val claimOwnerId = claim.teamId ?: claim.playerId
            
            val newPositions = visualisationService.displayGuildAware(
                playerId, areas, claimOwnerId, isGuildOwned, playerGuildId,
                "LIGHT_BLUE_GLAZED_TERRACOTTA", "LIGHT_BLUE_CARPET", 
                "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET"
            )
            playerState.visualisedClaims[claimId] = newPositions
            return
        }
    }
}
