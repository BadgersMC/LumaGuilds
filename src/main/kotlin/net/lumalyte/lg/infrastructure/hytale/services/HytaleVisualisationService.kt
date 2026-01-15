package net.lumalyte.lg.infrastructure.hytale.services

import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.math.vector.Vector3d
import com.hypixel.hytale.protocol.Color
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position3D
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Hytale implementation of VisualisationService using particle effects for claim boundaries.
 *
 * Unlike block-based visualization in Minecraft, Hytale uses particle systems to show claim boundaries.
 * This is more performant and doesn't interfere with the actual world blocks.
 */
class HytaleVisualisationService : VisualisationService {

    private val log = LoggerFactory.getLogger(HytaleVisualisationService::class.java)

    // Particle system IDs for different visualization types
    // These should match particle system definitions in Hytale assets
    private val SELECTED_PARTICLE = "hytale:yellow_sparkle"
    private val EDGE_PARTICLE = "hytale:blue_outline"
    private val CORNER_PARTICLE = "hytale:green_marker"
    private val GUILD_OWNED_PARTICLE = "hytale:cyan_outline"
    private val FRIENDLY_GUILD_PARTICLE = "hytale:green_outline"
    private val ENEMY_GUILD_PARTICLE = "hytale:red_outline"

    override fun displaySelected(playerId: UUID, position: Position3D, block: String, surfaceBlock: String) {
        val playerRef = Universe.get().getPlayer(playerId) ?: run {
            log.debug("Cannot display selected position for offline player $playerId")
            return
        }

        val worldUuid = playerRef.getWorldUuid() ?: return
        val world = Universe.get().getWorld(worldUuid) ?: return
        val entityStore = world.entityStore
        val accessor = entityStore.store

        // Convert position to Vector3d with +0.5 offset for center of block
        val particlePos = Vector3d(
            position.x.toDouble() + 0.5,
            position.y.toDouble() + 0.5,
            position.z.toDouble() + 0.5
        )

        // Get player entity reference for visibility
        val playerEntityRef = playerRef.getReference()
        if (playerEntityRef == null || !playerEntityRef.isValid()) return

        val playerRefs = listOf(playerEntityRef)

        // Spawn yellow sparkle particle at selected position
        val yellowColor = Color(255.toByte(), 255.toByte(), 0.toByte())
        ParticleUtil.spawnParticleEffect(
            SELECTED_PARTICLE,
            particlePos,
            1.0f,  // scale
            0.0f,  // yaw
            0.0f,  // pitch
            1.0f,  // alpha
            yellowColor,
            playerRefs,
            accessor
        )
    }

    override fun displayComplete(
        playerId: UUID,
        areas: Set<Area>,
        edgeBlock: String,
        edgeSurfaceBlock: String,
        cornerBlock: String,
        cornerSurfaceBlock: String
    ): Set<Position3D> {
        val positions = mutableSetOf<Position3D>()

        for (area in areas) {
            positions.addAll(visualizeArea(playerId, area, EDGE_PARTICLE, CORNER_PARTICLE))
        }

        return positions
    }

    override fun displayPartitioned(
        playerId: UUID,
        areas: Set<Area>,
        edgeBlock: String,
        edgeSurfaceBlock: String,
        cornerBlock: String,
        cornerSurfaceBlock: String
    ): Set<Position3D> {
        // Same as displayComplete for particle-based visualization
        return displayComplete(playerId, areas, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)
    }

    override fun refreshComplete(
        playerId: UUID,
        existingPositions: Set<Position3D>,
        areas: Set<Area>,
        edgeBlock: String,
        edgeSurfaceBlock: String,
        cornerBlock: String,
        cornerSurfaceBlock: String
    ): Set<Position3D> {
        // Clear existing particles (in Hytale, particles auto-despawn so this is mostly tracking)
        // Then display new ones
        return displayComplete(playerId, areas, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)
    }

    override fun refreshPartitioned(
        playerId: UUID,
        existingPositions: Set<Position3D>,
        areas: Set<Area>,
        edgeBlock: String,
        edgeSurfaceBlock: String,
        cornerBlock: String,
        cornerSurfaceBlock: String
    ): Set<Position3D> {
        // Same as refreshComplete for particle-based visualization
        return refreshComplete(playerId, existingPositions, areas, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)
    }

    override fun clear(playerId: UUID, areas: Set<Position3D>) {
        // In Hytale, particles automatically despawn after their lifetime
        // We just need to track that we're no longer showing these positions
        log.debug("Clearing ${areas.size} particle positions for player $playerId")
    }

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
        val positions = mutableSetOf<Position3D>()

        // Determine particle color based on guild ownership
        val edgeParticle = when {
            !isGuildOwned -> EDGE_PARTICLE  // Personal claim - blue
            playerGuildId == claimOwnerId -> FRIENDLY_GUILD_PARTICLE  // Same guild - green
            playerGuildId != null -> ENEMY_GUILD_PARTICLE  // Different guild - red
            else -> GUILD_OWNED_PARTICLE  // Guild owned, player not in guild - cyan
        }

        for (area in areas) {
            positions.addAll(visualizeArea(playerId, area, edgeParticle, CORNER_PARTICLE))
        }

        return positions
    }

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
        // Execute refresh asynchronously
        // Note: Particle spawning should still happen on main thread in Hytale
        // So we prepare data async, then spawn particles on main thread
        val newPositions = refreshComplete(playerId, existingPositions, areas, edgeBlock, edgeSurfaceBlock, cornerBlock, cornerSurfaceBlock)
        callback(newPositions)
    }

    override fun clearAsync(playerId: UUID, positions: Set<Position3D>, callback: () -> Unit) {
        // Clear particles asynchronously
        clear(playerId, positions)
        callback()
    }

    // Private helper methods

    /**
     * Visualizes a single area with particles for edges and corners.
     */
    private fun visualizeArea(playerId: UUID, area: Area, edgeParticle: String, cornerParticle: String): Set<Position3D> {
        val positions = mutableSetOf<Position3D>()

        val playerRef = Universe.get().getPlayer(playerId) ?: return positions
        val worldUuid = playerRef.getWorldUuid() ?: return positions
        val world = Universe.get().getWorld(worldUuid) ?: return positions
        val entityStore = world.entityStore
        val accessor = entityStore.store

        val playerEntityRef = playerRef.getReference()
        if (playerEntityRef == null || !playerEntityRef.isValid()) return positions

        val playerRefs = listOf(playerEntityRef)

        // Spawn particles along edges
        val minX = area.lowerPosition2D.x
        val maxX = area.upperPosition2D.x
        val minZ = area.lowerPosition2D.z
        val maxZ = area.upperPosition2D.z
        val y = 64.0 + 0.5  // Default ground level - TODO: get actual ground level from world

        // Bottom edge (minZ)
        for (x in minX..maxX) {
            spawnEdgeParticle(x, y, minZ, edgeParticle, playerRefs, accessor)
            positions.add(Position3D(x, y.toInt(), minZ))
        }

        // Top edge (maxZ)
        for (x in minX..maxX) {
            spawnEdgeParticle(x, y, maxZ, edgeParticle, playerRefs, accessor)
            positions.add(Position3D(x, y.toInt(), maxZ))
        }

        // Left edge (minX)
        for (z in (minZ + 1) until maxZ) {
            spawnEdgeParticle(minX, y, z, edgeParticle, playerRefs, accessor)
            positions.add(Position3D(minX, y.toInt(), z))
        }

        // Right edge (maxX)
        for (z in (minZ + 1) until maxZ) {
            spawnEdgeParticle(maxX, y, z, edgeParticle, playerRefs, accessor)
            positions.add(Position3D(maxX, y.toInt(), z))
        }

        // Spawn particles at corners with different particle type
        spawnCornerParticle(minX, y, minZ, cornerParticle, playerRefs, accessor)
        spawnCornerParticle(maxX, y, minZ, cornerParticle, playerRefs, accessor)
        spawnCornerParticle(minX, y, maxZ, cornerParticle, playerRefs, accessor)
        spawnCornerParticle(maxX, y, maxZ, cornerParticle, playerRefs, accessor)

        positions.add(Position3D(minX, y.toInt(), minZ))
        positions.add(Position3D(maxX, y.toInt(), minZ))
        positions.add(Position3D(minX, y.toInt(), maxZ))
        positions.add(Position3D(maxX, y.toInt(), maxZ))

        return positions
    }

    /**
     * Spawns an edge particle at the specified position.
     */
    private fun spawnEdgeParticle(
        x: Int,
        y: Double,
        z: Int,
        particleId: String,
        playerRefs: List<Ref<EntityStore>>,
        accessor: ComponentAccessor<EntityStore>
    ) {
        val position = Vector3d(x.toDouble() + 0.5, y, z.toDouble() + 0.5)
        val blueColor = Color(0.toByte(), 150.toByte(), 255.toByte())

        ParticleUtil.spawnParticleEffect(
            particleId,
            position,
            0.8f,  // scale
            0.0f,  // yaw
            0.0f,  // pitch
            0.9f,  // alpha
            blueColor,
            playerRefs,
            accessor
        )
    }

    /**
     * Spawns a corner particle at the specified position (larger and more visible).
     */
    private fun spawnCornerParticle(
        x: Int,
        y: Double,
        z: Int,
        particleId: String,
        playerRefs: List<Ref<EntityStore>>,
        accessor: ComponentAccessor<EntityStore>
    ) {
        val position = Vector3d(x.toDouble() + 0.5, y, z.toDouble() + 0.5)
        val greenColor = Color(0.toByte(), 255.toByte(), 100.toByte())

        ParticleUtil.spawnParticleEffect(
            particleId,
            position,
            1.2f,  // larger scale for corners
            0.0f,  // yaw
            0.0f,  // pitch
            1.0f,  // full alpha
            greenColor,
            playerRefs,
            accessor
        )
    }
}
