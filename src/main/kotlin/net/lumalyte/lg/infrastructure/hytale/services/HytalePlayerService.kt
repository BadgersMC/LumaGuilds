package net.lumalyte.lg.infrastructure.hytale.services

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.protocol.SoundCategory
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.domain.values.InventoryView
import net.lumalyte.lg.domain.values.Position3D
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Hytale implementation of PlayerService.
 *
 * This service integrates with Hytale's native player management APIs
 * to provide player-related operations like messaging, position tracking,
 * inventory management, and permission checks.
 *
 * Uses Universe.get() to access the global Universe instance and PlayerRef
 * for individual player operations.
 */
class HytalePlayerService : PlayerService {

    private val log = LoggerFactory.getLogger(HytalePlayerService::class.java)

    /**
     * Gets the Universe instance.
     */
    private fun getUniverse(): Universe {
        return Universe.get()
    }

    override fun sendMessage(playerId: UUID, message: String) {
        try {
            val universe = getUniverse()
            val playerRef = universe.getPlayer(playerId)

            if (playerRef == null) {
                log.warn("Cannot send message to player $playerId - player not found")
                return
            }

            playerRef.sendMessage(Message.raw(message))
        } catch (e: Exception) {
            log.error("Error sending message to player $playerId", e)
        }
    }

    override fun getPlayerName(playerId: UUID): String? {
        try {
            val universe = getUniverse()
            val playerRef = universe.getPlayer(playerId)
            return playerRef?.username
        } catch (e: Exception) {
            log.error("Error getting player name for $playerId", e)
            return null
        }
    }

    override fun isPlayerOnline(playerId: UUID): Boolean {
        try {
            val universe = getUniverse()
            val playerRef = universe.getPlayer(playerId)
            return playerRef != null
        } catch (e: Exception) {
            log.error("Error checking if player $playerId is online", e)
            return false
        }
    }

    override fun getOnlinePlayers(): List<UUID> {
        try {
            val universe = getUniverse()
            // Use universe.getPlayers() which returns List<PlayerRef>
            return universe.players.mapNotNull { it.getUuid() }
        } catch (e: Exception) {
            log.error("Error getting online players", e)
            return emptyList()
        }
    }

    override fun hasPermission(playerId: UUID, permission: String): Boolean {
        try {
            val universe = getUniverse()
            val playerRef = universe.getPlayer(playerId)

            if (playerRef == null) {
                log.debug("Player $playerId not found for permission check")
                return false
            }

            // Get the entity reference
            val ref = playerRef.reference ?: return false
            if (!ref.isValid()) return false

            // Get the world to access the entity store
            val worldUuid = playerRef.getWorldUuid()
            if (worldUuid == null) {
                log.debug("World UUID not found for player $playerId")
                return false
            }

            val world = universe.getWorld(worldUuid)
            if (world == null) {
                log.debug("World not found for player $playerId")
                return false
            }

            val store = world.entityStore.store
            val player = store.getComponent(ref, Player.getComponentType())

            if (player == null) {
                log.debug("Player component not found for $playerId")
                return false
            }

            return player.hasPermission(permission)
        } catch (e: Exception) {
            log.error("Error checking permission for player $playerId", e)
            return false
        }
    }

    override fun getPlayerInventory(playerId: UUID): InventoryView? {
        // TODO: Implement inventory access when Hytale's inventory API is fully available
        // The inventory system in Hytale uses components and may require more complex access
        log.debug("getPlayerInventory not yet implemented for player $playerId")
        return null
    }

    override fun openInventory(playerId: UUID, inventory: InventoryView) {
        // TODO: Implement inventory GUI opening when Hytale's inventory API is fully available
        // This will likely use custom UI pages similar to how we use InteractiveCustomUIPage
        log.debug("openInventory not yet implemented for player $playerId")
    }

    override fun closeInventory(playerId: UUID) {
        // TODO: Implement inventory closing when Hytale's inventory API is fully available
        log.debug("closeInventory not yet implemented for player $playerId")
    }

    override fun getPlayerPosition(playerId: UUID): Position3D? {
        try {
            val universe = getUniverse()
            val playerRef = universe.getPlayer(playerId)

            if (playerRef == null) {
                log.debug("Player $playerId not found for position lookup")
                return null
            }

            val transform = playerRef.getTransform()
            val position = transform.getPosition()

            return Position3D(
                x = position.x.toInt(),
                y = position.y.toInt(),
                z = position.z.toInt()
            )
        } catch (e: Exception) {
            log.error("Error getting position for player $playerId", e)
            return null
        }
    }

    override fun getPlayerWorld(playerId: UUID): UUID? {
        try {
            val universe = getUniverse()
            val playerRef = universe.getPlayer(playerId)
            return playerRef?.getWorldUuid()
        } catch (e: Exception) {
            log.error("Error getting world for player $playerId", e)
            return null
        }
    }

    override fun playSound(playerId: UUID, sound: String, volume: Float, pitch: Float) {
        // Hytale uses SoundUtil with integer sound event IDs, not string names
        // The PlayerService interface uses string names for platform-agnostic support
        // To implement this, we would need a sound name -> sound event ID mapping
        //
        // Example of how it would work once we have the mapping:
        // val soundEventId = getSoundEventId(sound) // Would need sound registry/mapping
        // val playerRef = universe.getPlayer(playerId)
        // SoundUtil.playSoundEvent2dToPlayer(playerRef, soundEventId, SoundCategory.MASTER, volume, pitch)

        log.debug("playSound not yet fully implemented - requires sound name to ID mapping (sound: $sound, player: $playerId)")
        // TODO: Implement sound name -> event ID mapping from Hytale's asset system
    }
}
