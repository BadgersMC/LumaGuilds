package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages one [ItemDisplay] per online player in a bannerman-enabled guild.
 * Each display is tagged with a PDC key so we can sweep orphans after a crash.
 */
internal class BannermanRenderService(private val plugin: JavaPlugin) {

    private val tagKey = NamespacedKey(plugin, "bannerman_owner")

    private val displays = ConcurrentHashMap<UUID, UUID>()

    /**
     * Spawn (or respawn) a banner display attached to the player. The previous display, if any, is removed.
     * The display rides the player as a passenger so the client renders it rigidly attached —
     * no per-tick teleports, no position/yaw desync. The owner is hidden from the entity so it
     * never clips into their first-person FOV; teammates and enemies still see it.
     */
    fun spawnFor(player: Player, banner: ItemStack) {
        despawnFor(player.uniqueId)
        val display = player.world.spawn(
            player.location,
            ItemDisplay::class.java,
        ) { d ->
            d.setItemStack(banner)
            d.isPersistent = false
            d.transformation = backTransformation()
            d.persistentDataContainer.set(tagKey, PersistentDataType.STRING, player.uniqueId.toString())
        }
        if (!player.addPassenger(display)) {
            display.remove()
            return
        }
        player.hideEntity(plugin, display)
        displays[player.uniqueId] = display.uniqueId
    }

    /** Swap the rendered ItemStack on a player's existing display, if any. */
    fun updateBanner(playerId: UUID, banner: ItemStack) {
        val display = currentDisplay(playerId) ?: return
        display.setItemStack(banner)
    }

    /** Remove the player's display, if any. */
    fun despawnFor(playerId: UUID) {
        val display = currentDisplay(playerId)
        display?.remove()
        displays.remove(playerId)
    }

    fun isTracking(playerId: UUID): Boolean = displays.containsKey(playerId)

    /** Returns the live ItemDisplay entity, or null if not spawned (or the entity has since been removed). */
    fun currentDisplay(playerId: UUID): ItemDisplay? {
        val entityId = displays[playerId] ?: return null
        return Bukkit.getEntity(entityId) as? ItemDisplay
    }

    /** Removes all tracked displays. Call on plugin disable. */
    fun despawnAll() {
        displays.keys.toList().forEach { despawnFor(it) }
    }

    /**
     * Sweep every loaded world for ItemDisplay entities tagged as ours. Used at plugin enable
     * to clean up orphans left behind by a server crash.
     */
    fun sweepOrphans() {
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (entity !is ItemDisplay) continue
                if (entity.persistentDataContainer.has(tagKey, PersistentDataType.STRING)) {
                    entity.remove()
                }
            }
        }
    }

    @Suppress("MagicNumber")
    private fun backTransformation(): Transformation = Transformation(
        // Origin sits at the player's passenger mount (~head height). Drop down to the upper
        // back and offset behind the torso.
        Vector3f(0f, -0.7f, -0.25f),
        Quaternionf().rotateY(Math.PI.toFloat()), // face backwards relative to player
        Vector3f(1.0f, 1.5f, 1.0f), // taller than wide
        Quaternionf(),
    )
}
