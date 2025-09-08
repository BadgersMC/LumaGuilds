package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.adapters.bukkit.toLocation
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.utils.carpetBlocks
import net.lumalyte.lg.utils.toward
import net.lumalyte.lg.utils.transparentMaterials
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

private const val upperRange = 5
private const val lowerRange = 50

/**
 * Data class to hold four values for visualization colors.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

class VisualisationServiceBukkit: VisualisationService {

    companion object {
        private val logger = Logger.getLogger("VisualisationServiceBukkit")
    }
    enum class Direction {
        North,
        South,
        East,
        West
    }

    override fun displaySelected(
        playerId: UUID,
        position: Position3D,
        block: String,
        surfaceBlock: String
    ) {
        val player = Bukkit.getPlayer(playerId) ?: return
        setVisualisedBlock(player, position, Material.valueOf(block), Material.valueOf(surfaceBlock))
    }

    /**
     * Visualize a claim with only the outer borders shown.
     */
    override fun displayComplete(playerId: UUID, areas: Set<Area>, edgeBlock: String,
                                 edgeSurfaceBlock: String, cornerBlock: String,
                                 cornerSurfaceBlock: String): Set<Position3D> {
        // Get player if they exist
        val player = Bukkit.getPlayer(playerId) ?: return emptySet()

        // Get positions and set visualisations on blocks
        val borders = get3DOuterBorders(areas, player.location)
        val corners = get3DPartitionedCorners(areas, player.location)
        setVisualisedBlocks(player, borders, Material.valueOf(edgeBlock), Material.valueOf(edgeSurfaceBlock))
        setVisualisedBlocks(player, corners, Material.valueOf(cornerBlock), Material.valueOf(cornerSurfaceBlock))
        return (borders + corners)
    }

    /**
     * Visualise a claim with individual partitions shown.
     */
    override fun displayPartitioned(playerId: UUID, areas: Set<Area>, edgeBlock: String,
                                    edgeSurfaceBlock: String, cornerBlock: String,
                                    cornerSurfaceBlock: String): Set<Position3D> {
        // Get player if they exist
        val player = Bukkit.getPlayer(playerId) ?: return emptySet()

        // Get block positions
        val borders = get3DPartitionedBorders(areas, player.location)
        val corners = get3DPartitionedCorners(areas, player.location)

        // Set visualisations on blocks
        setVisualisedBlocks(player, borders, Material.valueOf(edgeBlock), Material.valueOf(edgeSurfaceBlock))
        setVisualisedBlocks(player, corners, Material.valueOf(cornerBlock), Material.valueOf(cornerSurfaceBlock))
        return (borders + corners)
    }

    /**
     * Display claim visualization with guild ownership awareness.
     * This method respects guild boundaries and shows appropriate colors based on ownership.
     */
    override fun displayGuildAware(
        playerId: UUID,
        areas: Set<Area>,
        claimOwnerId: UUID,
        isGuildOwned: Boolean,
        playerGuildId: UUID?,
        edgeBlock: String,
        edgeSurfaceBlock: String,
        cornerBlock: String,
        cornerSurfaceBlock: String
    ): Set<Position3D> {
        logger.info("[DEBUG] displayGuildAware() called for player $playerId with ${areas.size} areas")

        // Check if Bukkit server is available (for test environments)
        if (Bukkit.getServer() == null) {
            logger.warning("[WARN] Bukkit server not available, returning empty set")
            return emptySet()
        }

        // Get player if they exist
        val player = Bukkit.getPlayer(playerId)
        if (player == null) {
            logger.warning("[WARN] Player $playerId not found online, returning empty set")
            return emptySet()
        }

        logger.info("[DEBUG] Player ${player.name} found, proceeding with visualization")
        logger.info("[DEBUG] Input materials: edge=$edgeBlock, edgeSurface=$edgeSurfaceBlock, corner=$cornerBlock, cornerSurface=$cornerSurfaceBlock")

        // Determine visualization colors based on ownership and guild relationships
        val (finalEdgeBlock, finalEdgeSurfaceBlock, finalCornerBlock, finalCornerSurfaceBlock) =
            determineGuildAwareColors(claimOwnerId, isGuildOwned, playerGuildId, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)

        logger.info("[DEBUG] Final materials after guild-aware processing: edge=$finalEdgeBlock, edgeSurface=$finalEdgeSurfaceBlock, corner=$finalCornerBlock, cornerSurface=$finalCornerSurfaceBlock")

        // Validate materials before using them
        try {
            val edgeMaterial = Material.valueOf(finalEdgeBlock)
            val edgeSurfaceMaterial = Material.valueOf(finalEdgeSurfaceBlock)
            val cornerMaterial = Material.valueOf(finalCornerBlock)
            val cornerSurfaceMaterial = Material.valueOf(finalCornerSurfaceBlock)

            logger.info("[DEBUG] All materials validated successfully")

            // Get positions and set visualisations on blocks
            logger.info("[DEBUG] Calculating 3D border positions...")
            val borders = get3DOuterBorders(areas, player.location)
            logger.info("[DEBUG] Found ${borders.size} border positions")

            val corners = get3DPartitionedCorners(areas, player.location)
            logger.info("[DEBUG] Found ${corners.size} corner positions")

            logger.info("[DEBUG] Setting visualised blocks for borders...")
            setVisualisedBlocks(player, borders, edgeMaterial, edgeSurfaceMaterial)

            logger.info("[DEBUG] Setting visualised blocks for corners...")
            setVisualisedBlocks(player, corners, cornerMaterial, cornerSurfaceMaterial)

            val totalPositions = borders.size + corners.size
            logger.info("[DEBUG] displayGuildAware() completed successfully: $totalPositions positions visualized")
            return (borders + corners)

        } catch (e: IllegalArgumentException) {
            logger.severe("[ERROR] Invalid material name in displayGuildAware(): ${e.message}")
            logger.severe("[ERROR] Failed materials: edge=$finalEdgeBlock, edgeSurface=$finalEdgeSurfaceBlock, corner=$finalCornerBlock, cornerSurface=$finalCornerSurfaceBlock")
            e.printStackTrace()
            return emptySet()
        } catch (e: Exception) {
            logger.severe("[ERROR] Unexpected error in displayGuildAware(): ${e.message}")
            e.printStackTrace()
            return emptySet()
        }
    }

    override fun refreshComplete(playerId: UUID, existingPositions: Set<Position3D>, areas: Set<Area>,
                                 edgeBlock: String, edgeSurfaceBlock: String, cornerBlock: String,
                                 cornerSurfaceBlock: String): Set<Position3D> {
        val border = displayComplete(playerId, areas, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)
        val borderToRemove = existingPositions.toMutableSet()
        borderToRemove.removeAll(border)
        clear(playerId, borderToRemove)
        return border
    }

    override fun refreshPartitioned(playerId: UUID, existingPositions: Set<Position3D>, areas: Set<Area>,
                                    edgeBlock: String, edgeSurfaceBlock: String, cornerBlock: String, cornerSurfaceBlock: String): Set<Position3D> {
        val border = displayPartitioned(playerId, areas, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)
        val borderToRemove = existingPositions.toMutableSet()
        borderToRemove.removeAll(border)
        clear(playerId, borderToRemove)
        return border
    }

    /**
     * Refresh visualization asynchronously to improve performance.
     */
    override fun refreshAsync(
        playerId: UUID,
        existingPositions: Set<Position3D>,
        areas: Set<Area>,
        edgeBlock: String,
        edgeSurfaceBlock: String,
        cornerBlock: String,
        cornerSurfaceBlock: String,
        callback: (Set<Position3D>) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        logger.info("[DEBUG] refreshAsync() started for player $playerId at $startTime")

        // Run the heavy computation on a background thread
        CompletableFuture.supplyAsync {
            val calcStartTime = System.currentTimeMillis()
            logger.info("[DEBUG] Starting async position calculation for ${areas.size} areas")

            try {
                // Calculate new positions (this is CPU-intensive)
                val newPositions = calculateVisualizationPositions(areas)
                val calcEndTime = System.currentTimeMillis()
                val calcDuration = calcEndTime - calcStartTime

                logger.info("[DEBUG] Position calculation completed in ${calcDuration}ms, found ${newPositions.size} positions")
                newPositions
            } catch (e: Exception) {
                logger.severe("[ERROR] Failed to calculate visualization positions: ${e.message}")
                e.printStackTrace()
                emptySet<Position2D>()
            }
        }.thenAcceptAsync { newPositions ->
            val mainThreadStartTime = System.currentTimeMillis()
            logger.info("[DEBUG] Switching to main thread for visualization at $mainThreadStartTime")

            // Apply the visualization on the main thread (Bukkit requirement)
            val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
            if (plugin == null) {
                logger.severe("[ERROR] LumaGuilds plugin not found, cannot schedule main thread task")
                return@thenAcceptAsync
            }

            try {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val mainThreadExecutionTime = System.currentTimeMillis()
                    logger.info("[DEBUG] Main thread execution started at $mainThreadExecutionTime")

                    val player = Bukkit.getPlayer(playerId)
                    if (player == null || !player.isOnline) {
                        logger.warning("[WARN] Player $playerId not found or not online, skipping visualization")
                        return@Runnable
                    }

                    try {
                        logger.info("[DEBUG] Clearing ${existingPositions.size} existing positions")
                        clear(playerId, existingPositions)

                        logger.info("[DEBUG] Calculating 3D positions for ${newPositions.size} 2D positions")
                        val borders = get3DPositions(newPositions, player.location)
                        val corners = get3DPartitionedCorners(areas, player.location)

                        logger.info("[DEBUG] Setting ${borders.size} border blocks and ${corners.size} corner blocks")

                        // Validate materials before using them
                        val edgeMaterial = Material.valueOf(edgeBlock)
                        val edgeSurfaceMaterial = Material.valueOf(edgeSurfaceBlock)
                        val cornerMaterial = Material.valueOf(cornerBlock)
                        val cornerSurfaceMaterial = Material.valueOf(cornerSurfaceBlock)

                        setVisualisedBlocks(player, borders, edgeMaterial, edgeSurfaceMaterial)
                        setVisualisedBlocks(player, corners, cornerMaterial, cornerSurfaceMaterial)

                        val totalPositions = borders.size + corners.size
                        logger.info("[DEBUG] Visualization completed: $totalPositions positions set")

                        // Call callback with new positions
                        val callbackStartTime = System.currentTimeMillis()
                        logger.info("[DEBUG] Calling callback at $callbackStartTime")
                        callback(borders + corners)

                        val endTime = System.currentTimeMillis()
                        val totalDuration = endTime - startTime
                        val mainThreadDuration = endTime - mainThreadStartTime
                        logger.info("[DEBUG] refreshAsync() completed in ${totalDuration}ms (main thread: ${mainThreadDuration}ms)")

                    } catch (e: IllegalArgumentException) {
                        logger.severe("[ERROR] Invalid material in refreshAsync: edge=$edgeBlock, edgeSurface=$edgeSurfaceBlock, corner=$cornerBlock, cornerSurface=$cornerSurfaceBlock")
                        logger.severe("[ERROR] Exception: ${e.message}")
                        e.printStackTrace()
                    } catch (e: Exception) {
                        logger.severe("[ERROR] Unexpected error in refreshAsync main thread execution: ${e.message}")
                        e.printStackTrace()
                    }
                })
            } catch (e: Exception) {
                logger.severe("[ERROR] Failed to schedule main thread task: ${e.message}")
                e.printStackTrace()
            }
        }.exceptionally { throwable ->
            val errorTime = System.currentTimeMillis()
            val duration = errorTime - startTime
            logger.severe("[ERROR] refreshAsync() failed after ${duration}ms: ${throwable.toString()}")
            null
        }
    }

    /**
     * Clear visualization asynchronously to improve performance.
     */
    override fun clearAsync(playerId: UUID, positions: Set<Position3D>, callback: () -> Unit) {
        val startTime = System.currentTimeMillis()
        logger.info("[DEBUG] clearAsync() started for player $playerId at $startTime, clearing ${positions.size} positions")

        // Run the clear operation on a background thread
        CompletableFuture.runAsync {
            try {
                val asyncStartTime = System.currentTimeMillis()
                logger.info("[DEBUG] Async clear operation started at $asyncStartTime")

                // Clear positions on main thread (Bukkit requirement)
                val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
                if (plugin == null) {
                    logger.severe("[ERROR] LumaGuilds plugin not found, cannot schedule clear task")
                    return@runAsync
                }

                val taskScheduleTime = System.currentTimeMillis()
                logger.info("[DEBUG] Scheduling main thread clear task at $taskScheduleTime")

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val mainThreadStartTime = System.currentTimeMillis()
                    logger.info("[DEBUG] Main thread clear execution started at $mainThreadStartTime")

                    try {
                        clear(playerId, positions)

                        val callbackStartTime = System.currentTimeMillis()
                        logger.info("[DEBUG] Calling clear callback at $callbackStartTime")
                        callback()

                        val endTime = System.currentTimeMillis()
                        val totalDuration = endTime - startTime
                        val mainThreadDuration = endTime - mainThreadStartTime
                        logger.info("[DEBUG] clearAsync() completed in ${totalDuration}ms (main thread: ${mainThreadDuration}ms)")

                    } catch (e: Exception) {
                        logger.severe("[ERROR] Error in clearAsync main thread execution: ${e.message}")
                        e.printStackTrace()
                    }
                })

                logger.info("[DEBUG] Main thread task scheduled successfully")

            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis()
                val duration = errorTime - startTime
                logger.severe("[ERROR] clearAsync() failed after ${duration}ms: ${e.message}")
                e.printStackTrace()
            }
        }.exceptionally { throwable ->
            val errorTime = System.currentTimeMillis()
            val duration = errorTime - startTime
            logger.severe("[ERROR] clearAsync() exceptionally failed after ${duration}ms: ${throwable.toString()}")
            null
        }
    }

    override fun clear(playerId: UUID, positions: Set<Position3D>) {
        logger.info("[DEBUG] clear() called for player $playerId, clearing ${positions.size} positions")

        // Get player if they exist
        val player = Bukkit.getPlayer(playerId)
        if (player == null) {
            logger.warning("[WARN] Player $playerId not found for clear operation")
            return
        }

        logger.info("[DEBUG] Player ${player.name} found, proceeding with clear operation")

        var successCount = 0
        var errorCount = 0

        for (position in positions) {
            try {
                val location = position.toLocation(player.world)
                val blockData = player.world.getBlockAt(location).blockData
                player.sendBlockChange(location, blockData)
                successCount++
            } catch (e: Exception) {
                logger.warning("[WARN] Failed to clear position $position: ${e.message}")
                errorCount++
            }
        }

        logger.info("[DEBUG] clear() completed: $successCount successful, $errorCount failed")
    }

    private fun setVisualisedBlocks(player: Player, positions: Set<Position3D>, block: Material, flatBlock: Material) {
        logger.info("[DEBUG] setVisualisedBlocks() called with ${positions.size} positions, block=$block, flatBlock=$flatBlock")
        var successCount = 0
        var errorCount = 0

        positions.forEach { position ->
            try {
                setVisualisedBlock(player, position, block, flatBlock)
                successCount++
            } catch (e: Exception) {
                logger.warning("[WARN] Failed to set visualised block at position $position: ${e.message}")
                errorCount++
            }
        }

        logger.info("[DEBUG] setVisualisedBlocks() completed: $successCount successful, $errorCount failed")
    }

    private fun setVisualisedBlock(player: Player, position: Position3D, block: Material, flatBlock: Material) {
        logger.fine("[TRACE] Setting visualised block at position $position with block=$block, flatBlock=$flatBlock")

        val blockLocation = Location(player.location.world, position.x.toDouble(),
            position.y.toDouble(), position.z.toDouble())

        logger.fine("[TRACE] Block location: world=${blockLocation.world?.name}, x=${blockLocation.x}, y=${blockLocation.y}, z=${blockLocation.z}")

        // Check if block location is valid
        if (blockLocation.world == null) {
            logger.warning("[WARN] Block location has null world for position $position")
            return
        }

        val currentBlockMaterial = blockLocation.block.blockData.material
        val isCarpetBlock = carpetBlocks.contains(currentBlockMaterial)

        logger.fine("[TRACE] Current block material: $currentBlockMaterial, isCarpetBlock=$isCarpetBlock")

        val blockData = if (isCarpetBlock) {
            logger.fine("[TRACE] Using flat block material: $flatBlock")
            flatBlock.createBlockData()
        } else {
            logger.fine("[TRACE] Using regular block material: $block")
            block.createBlockData()
        }

        logger.fine("[TRACE] Sending block change to player ${player.name}")
        player.sendBlockChange(blockLocation, blockData)
        logger.fine("[TRACE] Block change sent successfully")
    }

    private fun get3DOuterBorders(areas: Set<Area>, renderLocation: Location): Set<Position3D> {
        return get3DPositions(getOuterBorders(areas), renderLocation)
    }

    private fun get3DPartitionedBorders(areas: Set<Area>, renderLocation: Location): Set<Position3D> {
        val border = getPartitionedBorders(areas)
        return get3DPositions(border, renderLocation)
    }

    private fun get3DPartitionedCorners(areas: Set<Area>, renderLocation: Location): Set<Position3D> {
        val border = getPartitionedCorners(areas)
        return get3DPositions(border, renderLocation)
    }

    private fun getOuterBorders(areas: Set<Area>): Set<Position2D> {
        val borders = getPartitionedBorders(areas).toMutableSet()
        val resultingBorder = mutableSetOf<Position2D>()

        // Trace outer border
        val outerBorder = traceOuterBorder(areas)
        resultingBorder.addAll(outerBorder)
        borders.removeAll(outerBorder)

        // Trace all inner borders
        val innerBorders = traceInnerBorders(areas, borders, outerBorder)
        for (border in innerBorders) {
            resultingBorder.addAll(border)
        }
        return resultingBorder.toSet()
    }

    /**
     * Gets the 3D positions of the first solid blocks found in both upper and lower directions.
     * @param positions The set of positions to query.
     * @param renderLocation The position of the player as a starting point.
     * @return A set of 3D positions of solid blocks.
     */
    private fun get3DPositions(positions: Set<Position2D>, renderLocation: Location): Set<Position3D> {
        val visualisedBlocks: MutableSet<Position3D> = mutableSetOf()
        for (position in positions) {
            findSolidBlock(position, renderLocation, upperRange, 1)?.let { visualisedBlocks.add(it) }
            findSolidBlock(position, renderLocation, lowerRange, -1)?.let { visualisedBlocks.add(it) }
        }
        return visualisedBlocks
    }

    /**
     * Gets the first solid block when querying each block up or down from a starting position.
     * @param position The 2D position in the world.
     * @param renderLocation The position of the player as a starting point.
     * @param range How many blocks to search.
     * @param direction The direction to check, 1 for up and -1 for down.
     * @return The 3D position of the first solid block.
     */
    private fun findSolidBlock(position: Position2D, renderLocation: Location,
                               range: Int, direction: Int): Position3D? {
        val startY = if (direction > 0) renderLocation.blockY + 1 else renderLocation.blockY
        val endY = renderLocation.blockY + direction * range
        for (y in startY toward endY) {
            val blockLocation = Location(
                renderLocation.world, position.x.toDouble(),
                y.toDouble(), position.z.toDouble()
            )
            if (transparentMaterials.contains(blockLocation.block.blockData.material)) continue
            if (!isBlockVisible(blockLocation)) continue
            return blockLocation.toPosition3D()
        }
        return null
    }

    /**
     * Determine if a block is a floor/ceiling and therefore should be considered visible
     * @param location The location of the block.
     * @return True if the block is considered visible.
     */
    private fun isBlockVisible(location: Location): Boolean {
        val above = Location(location.world, location.x, location.y + 1, location.z).block.blockData.material
        val below = Location(location.world, location.x, location.y - 1, location.z).block.blockData.material
        return transparentMaterials.contains(above) || transparentMaterials.contains(below)
    }

    private fun getPartitionedBorders(areas: Set<Area>): Set<Position2D> {
        val borders: MutableSet<Position2D> = mutableSetOf()
        for (area in areas) {
            borders.addAll(area.getEdgeBlockPositions())
        }
        return borders
    }

    private fun getPartitionedCorners(areas: Set<Area>): Set<Position2D> {
        val corners: MutableSet<Position2D> = mutableSetOf()
        for (area in areas) {
            corners.addAll(area.getCornerBlockPositions())
        }
        return corners
    }

    /**
     * Gets the cardinal direction movement from one Position to another.
     * @param first The starting position.
     * @param second The position to move to.
     * @return The Direction enum that is being moved to.
     */
    private fun getTravelDirection(first: Position2D, second: Position2D): Direction {
        return when {
            second.z > first.z -> Direction.South
            second.z < first.z -> Direction.North
            second.x > first.x -> Direction.East
            else -> Direction.West
        }
    }

    /**
     * Determine if the position exists within a set of partitions.
     * @param position The 2D position in the world.
     * @param areas The set of areas to check against.
     * @return True if the position exists in at least one of the partitions.
     */
    private fun isPositionInPartitions(position: Position2D, areas: Set<Area>): Boolean {
        for (area in areas) {
            if (area.isPositionInArea(position)) {
                return true
            }
        }
        return false
    }

    /**
     * Trace the outer border of a claim.
     *
     * @param areas The list of areas defining positions.
     * @return The set of positions making up the outermost border of the claim.
     */
    private fun traceOuterBorder(areas: Set<Area>): Set<Position2D> {
        // Get starting position by finding the position with the largest x coordinate.
        val borders = mutableListOf<Position2D>()
        for (area in areas) {
            borders.addAll(area.getEdgeBlockPositions())
        }

        var startingPosition = borders[0]
        for (border in borders) {
            if (border.x > startingPosition.x) {
                startingPosition = border
            }
        }

        // Get second position by getting block either in front or to the right in a clockwise direction
        var currentPosition = findNextPosition(startingPosition, borders.toSet(), Position2D(0, 1), Position2D(-1, 0))
            ?: return setOf()

        return traceBorder(startingPosition, currentPosition, borders.toSet())
    }

    /**
     * Trace the inner border of a claim, given a border that already omits the outer border.
     *
     * The outer border must be omitted by running the outer border trace first and modifying the border list, otherwise
     * it will be included by this inner border function and cause potential issues.
     * @param borders The list of border block positions excluding the outer border.
     * @param outerBorder The list of border block positions of the outer border.
     * @return A set of sets, each being a collection of positions that make up a complete inner border
     */
    private fun traceInnerBorders(areas: Set<Area>, borders: Set<Position2D>, outerBorder: Set<Position2D>):
            Set<Set<Position2D>> {
        //val partitions = partitionService.getByClaim(claim)
        val queryBorders = borders.toMutableList()
        val resultingBorders: MutableSet<Set<Position2D>> = mutableSetOf()
        val checkedPositions = mutableSetOf<Position2D>()

        // Perform check for each border block
        while (queryBorders.isNotEmpty()) {
            val startingPosition = queryBorders[0]

            // A map of directions to move to depending on found direction (North, South, West, East)
            val directions = mapOf(
                Position2D(startingPosition.x, startingPosition.z - 1) to listOf(Position2D(1, 0), Position2D(0, 1)),
                Position2D(startingPosition.x, startingPosition.z + 1) to listOf(Position2D(-1, 0), Position2D(0, -1)),
                Position2D(startingPosition.x - 1, startingPosition.z) to listOf(Position2D(0, -1), Position2D(1, 0)),
                Position2D(startingPosition.x + 1, startingPosition.z) to listOf(Position2D(0, 1), Position2D(-1, 0))
            )

            // If on the edge, find the first block to navigate towards
            var currentPosition: Position2D = startingPosition
            for ((position, candidates) in directions) {
                if (!isPositionInPartitions(position, areas)) {
                    currentPosition = findNextPosition(currentPosition, borders, *candidates.toTypedArray()) ?: continue
                    break
                }
            }

            // Stop this iteration if no navigation is found
            if (currentPosition == startingPosition) {
                queryBorders.remove(startingPosition)
                checkedPositions.add(startingPosition)
                continue
            }

            // Trace using the found edge
            val mergedBorder = borders.toMutableList()
            mergedBorder.addAll(outerBorder)
            val innerBorder = traceBorder(startingPosition, currentPosition, mergedBorder.toSet())
            resultingBorders.add(innerBorder)
            checkedPositions.addAll(innerBorder)
            break
        }

        return resultingBorders
    }

    /**
     * Trace outer/inner border perimeter by rotating until the starting position is found.
     * @param startingPosition The starting position on the border.
     * @param nextPosition The following position on the border to move to.
     * @param borders The entire border structure to trace on.
     * @return The set of positions that make up the entire traced border.
     */
    private fun traceBorder(startingPosition: Position2D, nextPosition: Position2D,
                            borders: Set<Position2D>): Set<Position2D> {
        val resultingBorder = mutableSetOf<Position2D>()
        var previousPosition = startingPosition
        var currentPosition = nextPosition
        do {
            val newPosition: Position2D = when (getTravelDirection(previousPosition, currentPosition)) {
                Direction.North -> findNextPosition(currentPosition, borders,
                    Position2D(-1, 0), Position2D(0, -1), Position2D(1, 0))
                Direction.East -> findNextPosition(currentPosition, borders,
                    Position2D(0, -1), Position2D(1, 0), Position2D(0, 1))
                Direction.South -> findNextPosition(currentPosition, borders,
                    Position2D(1, 0), Position2D(0, 1), Position2D(-1, 0))
                else -> findNextPosition(currentPosition, borders,
                    Position2D(0, 1), Position2D(-1, 0), Position2D(0, -1))
            } ?: continue

            resultingBorder.add(newPosition)
            previousPosition = currentPosition
            currentPosition = newPosition
        } while (previousPosition != startingPosition)
        return resultingBorder
    }

    /**
     * Find the next valid position in the border map given directions to navigate towards.
     * @param current The current position.
     * @param borders The complete border structure to check against.
     * @param directions Relative directions from the current position to attempt to navigate towards.
     * @return The valid position that was found within the border structure, null if not found.
     */
    private fun findNextPosition(current: Position2D, borders: Set<Position2D>,
                                 vararg directions: Position2D): Position2D? {
        return directions.firstNotNullOfOrNull { direction ->
            borders.firstOrNull { it.x == current.x + direction.x && it.z == current.z + direction.z }
        }
    }

    /**
     * Determine visualization colors based on guild ownership and relationships.
     * This method implements the guild-aware color logic for claim boundaries.
     */
    private fun determineGuildAwareColors(
        claimOwnerId: UUID,
        isGuildOwned: Boolean,
        playerGuildId: UUID?,
        defaultEdgeBlock: String,
        defaultEdgeSurfaceBlock: String,
        defaultCornerBlock: String,
        defaultCornerSurfaceBlock: String
    ): Quadruple<String, String, String, String> {
        logger.info("[DEBUG] determineGuildAwareColors() called with claimOwnerId=$claimOwnerId, isGuildOwned=$isGuildOwned, playerGuildId=$playerGuildId")
        logger.info("[DEBUG] Default materials: edge=$defaultEdgeBlock, edgeSurface=$defaultEdgeSurfaceBlock, corner=$defaultCornerBlock, cornerSurface=$defaultCornerSurfaceBlock")

        val result = when {
            // Player owns this claim (either directly or through their guild)
            !isGuildOwned && playerGuildId == null -> {
                logger.info("[DEBUG] Case 1: Player owns claim directly - using friendly blue colors")
                Quadruple("LIGHT_BLUE_GLAZED_TERRACOTTA", "LIGHT_BLUE_CARPET", "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET")
            }
            isGuildOwned && playerGuildId == claimOwnerId -> {
                logger.info("[DEBUG] Case 2: Player's guild owns this claim - using friendly blue colors")
                Quadruple("LIGHT_BLUE_GLAZED_TERRACOTTA", "LIGHT_BLUE_CARPET", "BLUE_GLAZED_TERRACOTTA", "BLUE_CARPET")
            }
            isGuildOwned && playerGuildId != null && playerGuildId != claimOwnerId -> {
                logger.info("[DEBUG] Case 3: Different guild owns this claim - using neutral gray colors")
                Quadruple("LIGHT_GRAY_GLAZED_TERRACOTTA", "LIGHT_GRAY_CARPET", "GRAY_GLAZED_TERRACOTTA", "GRAY_CARPET")
            }
            !isGuildOwned && playerGuildId != null -> {
                logger.info("[DEBUG] Case 4: Different player owns this claim - using warning red colors")
                Quadruple("RED_GLAZED_TERRACOTTA", "RED_CARPET", "DARK_RED_GLAZED_TERRACOTTA", "RED_CARPET")
            }
            else -> {
                logger.info("[DEBUG] Case 5: Default case - using provided default colors")
                Quadruple(defaultEdgeBlock, defaultEdgeSurfaceBlock, defaultCornerBlock, defaultCornerSurfaceBlock)
            }
        }

        logger.info("[DEBUG] determineGuildAwareColors() returning: edge=${result.first}, edgeSurface=${result.second}, corner=${result.third}, cornerSurface=${result.fourth}")
        return result
    }

    /**
     * Calculate visualization positions without applying them.
     * This method is used for async operations to separate computation from rendering.
     */
    private fun calculateVisualizationPositions(areas: Set<Area>): Set<Position2D> {
        return getOuterBorders(areas)
    }
}
