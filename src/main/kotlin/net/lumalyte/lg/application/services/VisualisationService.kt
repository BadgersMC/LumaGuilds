package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

interface VisualisationService {
    fun displaySelected(playerId: UUID, position: Position3D, block: String, surfaceBlock: String)
    fun displayComplete(playerId: UUID, areas: Set<Area>, edgeBlock: String, edgeSurfaceBlock: String,
                        cornerBlock: String, cornerSurfaceBlock: String): Set<Position3D>
    fun displayPartitioned(playerId: UUID, areas: Set<Area>, edgeBlock: String, edgeSurfaceBlock: String,
                           cornerBlock: String, cornerSurfaceBlock: String): Set<Position3D>
    fun refreshComplete(playerId: UUID, existingPositions: Set<Position3D>, areas: Set<Area>,
                        edgeBlock: String, edgeSurfaceBlock: String, cornerBlock: String, cornerSurfaceBlock: String): Set<Position3D>
    fun refreshPartitioned(playerId: UUID, existingPositions: Set<Position3D>, areas: Set<Area>, edgeBlock: String,
                           edgeSurfaceBlock: String, cornerBlock: String, cornerSurfaceBlock: String): Set<Position3D>
    fun clear(playerId: UUID, areas: Set<Position3D>)
    
    /**
     * Display claim visualization with guild ownership awareness.
     * This method respects guild boundaries and shows appropriate colors based on ownership.
     * 
     * @param playerId The ID of the player viewing the visualization
     * @param areas The areas to visualize
     * @param claimOwnerId The ID of the claim owner (player or guild)
     * @param isGuildOwned Whether the claim is owned by a guild
     * @param playerGuildId The ID of the player's guild, if any
     * @param edgeBlock The block material for edges
     * @param edgeSurfaceBlock The surface block material for edges
     * @param cornerBlock The block material for corners
     * @param cornerSurfaceBlock The surface block material for corners
     * @return Set of visualized positions
     */
    fun displayGuildAware(
        playerId: UUID, 
        areas: Set<Area>, 
        claimOwnerId: UUID, 
        isGuildOwned: Boolean, 
        playerGuildId: UUID?,
        edgeBlock: String, 
        edgeSurfaceBlock: String, 
        cornerBlock: String, 
        cornerSurfaceBlock: String
    ): Set<Position3D>
    
    /**
     * Refresh visualization asynchronously to improve performance.
     * This method should be called from a background thread to avoid blocking the main thread.
     * 
     * @param playerId The ID of the player to refresh visualization for
     * @param existingPositions The currently visualized positions
     * @param areas The areas to visualize
     * @param edgeBlock The block material for edges
     * @param edgeSurfaceBlock The surface block material for edges
     * @param cornerBlock The block material for corners
     * @param cornerSurfaceBlock The surface block material for corners
     * @param callback Function to call when refresh is complete with new positions
     */
    fun refreshAsync(
        playerId: UUID, 
        existingPositions: Set<Position3D>, 
        areas: Set<Area>, 
        edgeBlock: String, 
        edgeSurfaceBlock: String, 
        cornerBlock: String, 
        cornerSurfaceBlock: String,
        callback: (Set<Position3D>) -> Unit
    )
    
    /**
     * Clear visualization asynchronously to improve performance.
     * This method should be called from a background thread to avoid blocking the main thread.
     * 
     * @param playerId The ID of the player to clear visualization for
     * @param positions The positions to clear
     * @param callback Function to call when clear is complete
     */
    fun clearAsync(playerId: UUID, positions: Set<Position3D>, callback: () -> Unit)
}
