package net.lumalyte.lg.application.actions.player.visualisation

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.PlayerState
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.collections.set
import kotlin.math.floor

class DisplayVisualisation(private val playerStateRepository: PlayerStateRepository,
                           private val claimRepository: ClaimRepository,
                           private val partitionRepository: PartitionRepository,
                           private val visualisationService: VisualisationService,
                           private val guildService: GuildService,
                           private val clearVisualisation: ClearVisualisation,
                           private val config: MainConfig) {

    companion object {
        private val logger = Logger.getLogger("DisplayVisualisation")
    }
    /**
     * Display claim visualisation to target player
     */
    fun execute(playerId: UUID, playerPosition: Position3D): MutableMap<UUID, Set<Position3D>> {
        logger.info("[DEBUG] DisplayVisualisation.execute() called for player: $playerId at position: $playerPosition")

        // Get player state with detailed logging
        var playerState = playerStateRepository.get(playerId)
        if (playerState == null) {
            logger.info("[DEBUG] Player state not found for $playerId, creating new PlayerState")
            playerState = PlayerState(playerId)
            playerStateRepository.add(playerState)
            logger.info("[DEBUG] New PlayerState created and added to repository")
        } else {
            logger.info("[DEBUG] Retrieved existing PlayerState for $playerId: claimToolMode=${playerState.claimToolMode}, isVisualising=${playerState.isVisualisingClaims}")
        }

        // Check refresh rate limiting
        val refreshPeriodInMillis = (config.visualiserRefreshPeriod * 1000).toLong()
        val lastVisualisation = playerState.lastVisualisationTime
        val currentTime = Instant.now()

        if (lastVisualisation != null) {
            val nextAllowedRefresh = lastVisualisation.plus(Duration.ofMillis(refreshPeriodInMillis))
            val isRateLimited = nextAllowedRefresh.isAfter(currentTime)
            logger.info("[DEBUG] Refresh rate check: lastVisualisation=$lastVisualisation, nextAllowed=$nextAllowedRefresh, current=$currentTime, rateLimited=$isRateLimited")

            if (isRateLimited) {
                logger.info("[DEBUG] Visualization request rate limited for player $playerId")
                return mutableMapOf()
            }
        } else {
            logger.info("[DEBUG] No previous visualization time found, allowing refresh")
        }

        // Clear existing visualization
        logger.info("[DEBUG] Clearing existing visualization for player $playerId")
        clearVisualisation.execute(playerId)

        // Determine visualization mode and execute
        val borders: MutableMap<UUID, Set<Position3D>> = mutableMapOf()
        val isPartitionedMode = playerState.claimToolMode == 1

        logger.info("[DEBUG] Visualization mode: ${if (isPartitionedMode) "PARTITIONED" else "COMPLETE"} (claimToolMode=${playerState.claimToolMode})")

        if (isPartitionedMode) {
            logger.info("[DEBUG] Executing partitioned visualization")
            borders.putAll(displayPartitioned(playerId, playerState, playerPosition))
        } else {
            logger.info("[DEBUG] Executing complete visualization")
            borders.putAll(displayComplete(playerId, playerState, playerPosition))
            playerState.visualisedClaims = borders
        }

        logger.info("[DEBUG] Visualization completed. Generated borders for ${borders.size} claims with ${borders.values.sumOf { it.size }} total positions")

        // Update player state
        logger.info("[DEBUG] Updating player state: cancelling hide timer, setting isVisualisingClaims=true")
        playerState.scheduledVisualiserHide?.cancel()
        playerState.isVisualisingClaims = true
        playerState.lastVisualisationTime = currentTime

        try {
            playerStateRepository.update(playerState)
            logger.info("[DEBUG] Player state updated successfully")
        } catch (e: Exception) {
            logger.severe("[ERROR] Failed to update player state for $playerId: ${e.message}")
            e.printStackTrace()
        }

        logger.info("[DEBUG] DisplayVisualisation.execute() completed successfully for player $playerId")
        return borders
    }

    /**
     * Visualise all of a player's claims with only outer borders.
     * Now uses guild-aware visualization for better ownership representation.
     */
    private fun displayComplete(playerId: UUID, playerState: PlayerState, playerPosition: Position3D): Map<UUID, Set<Position3D>> {
        logger.info("[DEBUG] displayComplete() called for player $playerId at position $playerPosition")

        val chunkPosition = Position2D(floor(playerPosition.x / 16.0).toInt(), floor(playerPosition.z / 16.0).toInt())
        logger.info("[DEBUG] Player chunk position: $chunkPosition")

        val chunks = getSurroundingChunks(chunkPosition, 16) // View distance fixed for now
        logger.info("[DEBUG] Scanning ${chunks.size} chunks around player")

        val partitions = chunks.flatMap { partitionRepository.getByChunk(it) }.toSet()
        logger.info("[DEBUG] Found ${partitions.size} partitions in surrounding chunks")

        if (partitions.isEmpty()) {
            logger.info("[DEBUG] No partitions found in surrounding chunks, returning empty map")
            return mutableMapOf()
        }

        // Get player's guild information for guild-aware visualization
        val playerGuilds = guildService.getPlayerGuilds(playerId)
        val playerGuildId = playerGuilds.firstOrNull()?.id
        logger.info("[DEBUG] Player guild info: guilds=${playerGuilds.size}, selectedGuildId=$playerGuildId")

        // Mapping claim ids to the positions assigned to that claim
        val visualised: MutableMap<UUID, Set<Position3D>> = mutableMapOf()
        var processedPartitions = 0
        var skippedDuplicates = 0

        for (partition in partitions) {
            if (visualised.containsKey(partition.claimId)) {
                skippedDuplicates++
                continue
            }

            logger.info("[DEBUG] Processing partition ${partition.id} for claim ${partition.claimId}")
            val claim = claimRepository.getById(partition.claimId)
            if (claim == null) {
                logger.warning("[WARN] Claim ${partition.claimId} not found for partition ${partition.id}")
                continue
            }

            logger.info("[DEBUG] Found claim ${claim.name} (${claim.id}), owner=${claim.playerId}, isGuildOwned=${claim.teamId != null}")

            // Handle claim not owned by this player
            if (claim.playerId != playerId) {
                logger.info("[DEBUG] Processing non-owned claim ${claim.name} with guild-aware visualization")
                val positions = handleNonOwnedClaimDisplay(playerId, claim, playerGuildId)
                val filteredPositions = positions.filter { it != playerState.selectedBlock }.toSet()
                visualised[claim.id] = filteredPositions
                logger.info("[DEBUG] Non-owned claim visualization: ${filteredPositions.size} positions generated")
            } else {
                // Get all partitions linked to found claim
                val claimPartitions = partitionRepository.getByClaim(claim.id)
                val areas = claimPartitions.map { it.area }.toMutableSet()
                logger.info("[DEBUG] Player's claim found with ${claimPartitions.size} partitions, total area: ${areas.size} areas")

                // Use guild-aware visualization for player's own claims
                val isGuildOwned = claim.teamId != null
                val claimOwnerId = claim.teamId ?: claim.playerId

                logger.info("[DEBUG] Calling displayGuildAware with materials: LIGHT_BLUE_GLAZED_TERRACOTTA, LIGHT_BLUE_CARPET, BLUE_GLAZED_TERRACOTTA, BLUE_CARPET")

                try {
                    val positions = visualisationService.displayGuildAware(
                        playerId, areas, claimOwnerId, isGuildOwned, playerGuildId,
                        "LIGHT_BLUE_GLAZED_TERRACOTTA", "LIGHT_BLUE_CARPET",
                        "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET"
                    ).filter { it != playerState.selectedBlock }.toSet()

                    visualised[claim.id] = positions
                    logger.info("[DEBUG] Guild-aware visualization completed: ${positions.size} positions generated for claim ${claim.name}")
                } catch (e: Exception) {
                    logger.severe("[ERROR] Failed to generate guild-aware visualization for claim ${claim.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
            processedPartitions++
        }

        logger.info("[DEBUG] displayComplete() completed: processed $processedPartitions partitions, skipped $skippedDuplicates duplicates, generated ${visualised.size} claim visualizations")
        return visualised
    }

    /**
     * Visualise a player's claims with individual partitions shown.
     * Now uses guild-aware visualization for better ownership representation.
     */
    private fun displayPartitioned(playerId: UUID, playerState: PlayerState, playerPosition: Position3D): Map<UUID, Set<Position3D>> {
        val chunkPosition = Position2D(floor(playerPosition.x / 16.0).toInt(), floor(playerPosition.z / 16.0).toInt())
        val chunks = getSurroundingChunks(chunkPosition, 16) // View distance fixed for now
        val partitions = chunks.flatMap { partitionRepository.getByChunk(it) }.toSet()
        if (partitions.isEmpty()) return mutableMapOf()

        // Get player's guild information for guild-aware visualization
        val playerGuilds = guildService.getPlayerGuilds(playerId)
        val playerGuildId = playerGuilds.firstOrNull()?.id

        // Mapping claim ids to the positions assigned to that claim
        val visualised: MutableMap<UUID, MutableSet<Position3D>> = mutableMapOf()
        for (partition in partitions) {
            val claim = claimRepository.getById(partition.claimId) ?: continue

            // Handle claim not owned by this player
            if (claim.playerId != playerId) {
                val positions = handleNonOwnedClaimDisplay(playerId, claim, playerGuildId).toMutableSet()
                visualised[claim.id] = positions
                playerState.visualisedClaims[claim.id] = positions
                continue
            }

            // Visualise the partition and add it to the map assigned to the partition's claim
            val newPositions = if (partition.area.isPositionInArea(claim.position)) {
                // Main partition - use guild-aware visualization
                val isGuildOwned = claim.teamId != null
                val claimOwnerId = claim.teamId ?: claim.playerId
                
                visualisationService.displayGuildAware(
                    playerId, setOf(partition.area), claimOwnerId, isGuildOwned, playerGuildId,
                    "CYAN_GLAZED_TERRACOTTA", "CYAN_CARPET",
                    "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET"
                ).filter { it != playerState.selectedBlock }.toSet()
            } else {
                // Attached partitions - use guild-aware visualization
                val isGuildOwned = claim.teamId != null
                val claimOwnerId = claim.teamId ?: claim.playerId
                
                visualisationService.displayGuildAware(
                    playerId, setOf(partition.area), claimOwnerId, isGuildOwned, playerGuildId,
                    "LIGHT_GRAY_GLAZED_TERRACOTTA", "LIGHT_GRAY_CARPET",
                    "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET"
                ).filter { it != playerState.selectedBlock }.toSet()
            }

            if (!visualised.containsKey(claim.id)) {
                visualised[claim.id] = mutableSetOf()
            }
            visualised[claim.id]?.addAll(newPositions)

            if (!playerState.visualisedPartitions.containsKey(claim.id)) {
                playerState.visualisedPartitions[claim.id] = mutableMapOf()
            }
            playerState.visualisedPartitions[claim.id]?.set(partition.id, newPositions)
        }
        return visualised
    }

    /**
     * Handle visualization for claims not owned by the player.
     * Now uses guild-aware visualization to show appropriate colors based on guild relationships.
     */
    private fun handleNonOwnedClaimDisplay(playerId: UUID, claim: Claim, playerGuildId: UUID?): Set<Position3D> {
        logger.info("[DEBUG] handleNonOwnedClaimDisplay() called for claim ${claim.name} (${claim.id}) owned by ${claim.playerId}")

        // Get all partitions linked to found claim
        val partitions = partitionRepository.getByClaim(claim.id)
        val areas = partitions.map { it.area }.toMutableSet()

        logger.info("[DEBUG] Retrieved ${partitions.size} partitions for non-owned claim, total areas: ${areas.size}")

        // Use guild-aware visualization to show appropriate colors
        val isGuildOwned = claim.teamId != null
        val claimOwnerId = claim.teamId ?: claim.playerId

        logger.info("[DEBUG] Guild-aware visualization for non-owned claim: isGuildOwned=$isGuildOwned, claimOwnerId=$claimOwnerId, playerGuildId=$playerGuildId")
        logger.info("[DEBUG] Using materials: RED_GLAZED_TERRACOTTA, RED_CARPET, BLACK_GLAZED_TERRACOTTA, BLACK_CARPET")

        try {
            val positions = visualisationService.displayGuildAware(
                playerId, areas, claimOwnerId, isGuildOwned, playerGuildId,
                "RED_GLAZED_TERRACOTTA", "RED_CARPET",
                "BLACK_GLAZED_TERRACOTTA", "BLACK_CARPET"
            )
            logger.info("[DEBUG] Non-owned claim visualization generated ${positions.size} positions")
            return positions
        } catch (e: Exception) {
            logger.severe("[ERROR] Failed to generate visualization for non-owned claim ${claim.name}: ${e.message}")
            e.printStackTrace()
            return emptySet()
        }
    }
    
    /**
     * Get a square of chunks centering on [chunkPosition] with a size of [radius]
     */
    private fun getSurroundingChunks(chunkPosition: Position2D, radius: Int): Set<Position2D> {
        val sideLength = (radius * 2) + 1 // Make it always odd (e.g. radius of 2 results in 5x5 square)
        val chunks: MutableSet<Position2D> = mutableSetOf()

        for (x in 0 until sideLength) {
            for (z in 0 until sideLength) {
                chunks.add(Position2D(chunkPosition.x + x - radius, chunkPosition.z + z - radius))
            }
        }

        return chunks
    }
}
