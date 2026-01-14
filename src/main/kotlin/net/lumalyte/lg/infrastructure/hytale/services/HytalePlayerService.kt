package net.lumalyte.lg.infrastructure.hytale.services

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.domain.values.InventoryView
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.hytale.adapters.*
import org.slf4j.LoggerFactory
import java.util.UUID

class HytalePlayerService : PlayerService {

    private val log = LoggerFactory.getLogger(HytalePlayerService::class.java)

    override fun sendMessage(playerId: UUID, message: String) {
        val playerRef = getPlayerRef(playerId) ?: run {
            log.debug("Cannot send message to offline player $playerId")
            return
        }

        // Use Hytale's messaging API
        playerRef.sendMessage(Message.raw(message))
    }

    override fun getPlayerName(playerId: UUID): String? {
        return getPlayerRef(playerId)?.getUsername()
    }

    override fun isPlayerOnline(playerId: UUID): Boolean {
        return Universe.get().getPlayer(playerId) != null
    }

    override fun getOnlinePlayers(): List<UUID> {
        return Universe.get().getPlayers().map { it.getUuid() }
    }

    override fun hasPermission(playerId: UUID, permission: String): Boolean {
        val player = getPlayer(playerId) ?: return false
        return player.hasPermission(permission)
    }

    override fun getPlayerInventory(playerId: UUID): InventoryView? {
        val player = getPlayer(playerId) ?: return null
        val inventory = player.getInventory()
        // Inventory.getStorage() returns ItemContainer which we can convert
        return inventory.getStorage().toInventoryView("Player Inventory")
    }

    override fun openInventory(playerId: UUID, inventory: InventoryView) {
        // TODO: Hytale GUI opening mechanism
        // This will likely require WindowManager from Player component
        log.warn("openInventory not yet implemented for Hytale")
    }

    override fun closeInventory(playerId: UUID) {
        val player = getPlayer(playerId) ?: return
        // TODO: Find correct API for closing inventory
        log.warn("closeInventory not yet implemented for Hytale")
    }

    override fun getPlayerPosition(playerId: UUID): Position3D? {
        val playerRef = getPlayerRef(playerId) ?: return null
        val transform = playerRef.getTransform()

        return Position3D(
            x = transform.getPosition().x.toInt(),
            y = transform.getPosition().y.toInt(),
            z = transform.getPosition().z.toInt()
        )
    }

    override fun getPlayerWorld(playerId: UUID): UUID? {
        return getPlayerRef(playerId)?.getWorldUuid()
    }

    override fun playSound(playerId: UUID, sound: String, volume: Float, pitch: Float) {
        // TODO: Hytale sound API
        log.warn("playSound not yet implemented for Hytale")
    }

    // Helper methods
    private fun getPlayerRef(playerId: UUID): PlayerRef? {
        return Universe.get().getPlayer(playerId)
    }

    private fun getPlayer(playerId: UUID): Player? {
        val playerRef = getPlayerRef(playerId) ?: return null
        val ref = playerRef.getReference() ?: return null

        if (!ref.isValid()) return null

        val worldUuid = playerRef.getWorldUuid() ?: return null
        val world = Universe.get().getWorld(worldUuid) ?: return null
        val entityStore = world.entityStore
        val store = entityStore.store

        return store.getComponent(ref, Player.getComponentType())
    }
}
