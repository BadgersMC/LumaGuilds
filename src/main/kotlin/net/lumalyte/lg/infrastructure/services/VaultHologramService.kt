package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.Plugin
import org.bukkit.util.RayTraceResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages hologram displays for guild vault chests.
 * Holograms only render when player has line of sight to the chest.
 */
class VaultHologramService(private val plugin: Plugin) : KoinComponent {

    private val guildRepository: GuildRepository by inject()
    private val logger = LoggerFactory.getLogger(VaultHologramService::class.java)

    // Map of vault location -> hologram entity UUID
    private val holograms = ConcurrentHashMap<String, UUID>()

    // Map of player UUID -> set of visible hologram locations
    private val playerVisibility = ConcurrentHashMap<UUID, MutableSet<String>>()

    private var updateTaskId: Int? = null

    companion object {
        private const val HOLOGRAM_HEIGHT = 1.5 // Height above chest block (0.5 blocks above top of chest)
        private const val CHECK_RADIUS = 512.0 // Max distance to check for visibility (32 chunks * 16 blocks/chunk)
        private const val UPDATE_INTERVAL = 10L // Ticks between visibility updates (0.5 seconds)
    }

    /**
     * Start the hologram update task
     */
    fun start() {
        updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            updateVisibility()
        }, UPDATE_INTERVAL, UPDATE_INTERVAL)

        logger.info("Vault hologram service started (LOS check every ${UPDATE_INTERVAL / 20.0}s)")
    }

    /**
     * Stop the hologram update task and cleanup
     */
    fun stop() {
        updateTaskId?.let { Bukkit.getScheduler().cancelTask(it) }
        removeAllHolograms()
        playerVisibility.clear()
        logger.info("Vault hologram service stopped")
    }

    /**
     * Create a hologram for a vault at the given location
     */
    fun createHologram(location: Location, guild: Guild) {
        val key = locationKey(location)

        // Remove existing hologram if present
        removeHologram(location)

        try {
            // Spawn TextDisplay entity above the chest
            val world = location.world ?: return
            val hologramLoc = location.clone().add(0.5, HOLOGRAM_HEIGHT, 0.5) // Center above chest

            val textDisplay = world.spawn(hologramLoc, TextDisplay::class.java) { display ->
                // Set the text
                display.text(net.kyori.adventure.text.Component.text("§6⚑ ${guild.name} Vault")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))

                // Display properties
                display.billboard = Display.Billboard.CENTER
                display.isSeeThrough = false
                display.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0) // Transparent background
                display.shadowRadius = 0.0f
                display.shadowStrength = 0.0f

                // Start invisible - will be made visible per-player based on LOS
                display.isVisibleByDefault = false

                // Make it persistent
                display.isPersistent = true
            }

            holograms[key] = textDisplay.uniqueId
            logger.debug("Created hologram for guild ${guild.name} at $key")

        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Failed to create hologram at $key", e)
        }
    }

    /**
     * Remove hologram at the given location
     */
    fun removeHologram(location: Location) {
        val key = locationKey(location)
        val hologramId = holograms.remove(key) ?: return

        // Find and remove the entity
        location.world?.getEntity(hologramId)?.remove()

        // Clear from player visibility tracking
        playerVisibility.values.forEach { it.remove(key) }

        logger.debug("Removed hologram at $key")
    }

    /**
     * Remove all holograms
     */
    fun removeAllHolograms() {
        holograms.values.forEach { hologramId ->
            Bukkit.getWorlds().forEach { world ->
                world.getEntity(hologramId)?.remove()
            }
        }
        holograms.clear()
        logger.debug("Removed all vault holograms")
    }

    /**
     * Update hologram visibility for all online players
     */
    private fun updateVisibility() {
        val onlinePlayers = Bukkit.getOnlinePlayers()

        for (player in onlinePlayers) {
            updatePlayerVisibility(player)
        }
    }

    /**
     * Update which holograms are visible to a specific player
     */
    private fun updatePlayerVisibility(player: Player) {
        val playerLoc = player.eyeLocation
        val visibleSet = playerVisibility.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }

        for ((locationKey, hologramId) in holograms) {
            val hologramEntity = getHologramEntity(hologramId) ?: continue
            val hologramLoc = hologramEntity.location

            // Check if in same world first (can't calculate distance across worlds)
            if (playerLoc.world != hologramLoc.world) {
                // Different world - hide if visible
                if (visibleSet.contains(locationKey)) {
                    player.hideEntity(plugin, hologramEntity)
                    visibleSet.remove(locationKey)
                }
                continue
            }

            // Check distance (cheaper than raytrace)
            val distance = playerLoc.distance(hologramLoc)
            if (distance > CHECK_RADIUS) {
                // Too far - hide if visible
                if (visibleSet.contains(locationKey)) {
                    player.hideEntity(plugin, hologramEntity)
                    visibleSet.remove(locationKey)
                }
                continue
            }

            // Check line of sight
            val hasLOS = hasLineOfSight(playerLoc, hologramLoc)

            if (hasLOS && !visibleSet.contains(locationKey)) {
                // Should be visible but isn't - show it
                player.showEntity(plugin, hologramEntity)
                visibleSet.add(locationKey)
            } else if (!hasLOS && visibleSet.contains(locationKey)) {
                // Should be hidden but isn't - hide it
                player.hideEntity(plugin, hologramEntity)
                visibleSet.remove(locationKey)
            }
        }
    }

    /**
     * Check if there's line of sight from the player to the hologram
     */
    private fun hasLineOfSight(from: Location, to: Location): Boolean {
        if (from.world != to.world) return false

        // Raytrace from player eye to hologram
        val direction = to.toVector().subtract(from.toVector())
        val distance = direction.length()

        val rayTraceResult: RayTraceResult? = from.world?.rayTraceBlocks(
            from,
            direction.normalize(),
            distance,
            org.bukkit.FluidCollisionMode.NEVER,
            true // Ignore passable blocks
        )

        // If raytrace hit nothing, we have LOS
        // If it hit something, check if it's very close to the target (within 0.5 blocks)
        return rayTraceResult == null ||
               (rayTraceResult.hitPosition?.distance(to.toVector()) ?: Double.MAX_VALUE) < 0.5
    }

    /**
     * Get the hologram entity by UUID
     */
    private fun getHologramEntity(hologramId: UUID): TextDisplay? {
        for (world in Bukkit.getWorlds()) {
            val entity = world.getEntity(hologramId)
            if (entity is TextDisplay) {
                return entity
            }
        }
        return null
    }

    /**
     * Handle player quit - cleanup their visibility tracking
     */
    fun onPlayerQuit(player: Player) {
        playerVisibility.remove(player.uniqueId)
    }

    /**
     * Convert location to a unique string key
     */
    private fun locationKey(location: Location): String {
        return "${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    /**
     * Recreate all holograms for existing vaults (called on plugin enable)
     */
    fun recreateAllHolograms() {
        val guilds = guildRepository.getAll()
        var count = 0

        for (guild in guilds) {
            if (guild.vaultStatus == net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
                val vaultLocation = guild.vaultChestLocation ?: continue

                val world = Bukkit.getWorld(vaultLocation.worldId) ?: continue
                val location = Location(world, vaultLocation.x.toDouble(), vaultLocation.y.toDouble(), vaultLocation.z.toDouble())

                createHologram(location, guild)
                count++
            }
        }

        logger.info("Recreated $count vault holograms")
    }
}
