package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
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
 *
 * The display is deliberately NOT mounted on the player. Position and rotation are
 * driven every tick by [BannermanTickTask] (head-follow + view-yaw), with client-side
 * interpolation smoothing the per-tick updates. The passenger pipeline was abandoned
 * because it never syncs rotation, and a teleport leaves the display behind (orphan
 * copy at the old spot). Manual driving has neither problem.
 */
internal class BannermanRenderService(private val plugin: JavaPlugin) {

    private val tagKey = NamespacedKey(plugin, "bannerman_owner")

    private val displays = ConcurrentHashMap<UUID, UUID>()

    companion object {
        /** The HEAD item-display context renders worn items slightly oversized; shrink a touch. */
        private const val BANNER_SCALE = 0.85f
    }

    /**
     * Spawn (or respawn) a banner display at the player's head position. The previous
     * display, if any, is removed. Billboard FIXED keeps the banner in world space
     * (it rotates with [BannermanTickTask], never faces a camera). No interpolation
     * durations are set: the tick task updates every tick, and each update should
     * apply immediately rather than lagging a tick behind the head.
     */
    fun spawnFor(player: Player, banner: ItemStack) {
        despawnFor(player.uniqueId)
        val display = player.world.spawn(
            BannermanPosition.headPosition(player.eyeLocation, player.pose),
            ItemDisplay::class.java,
        ) { d ->
            d.setItemStack(banner)
            d.isPersistent = false
            d.setBillboard(Display.Billboard.FIXED)
            // HEAD transform renders the banner exactly like a helmet-slot item:
            // the full-size banner block, matching vanilla "banner on head" behavior.
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD)
            d.setTransformation(
                Transformation(
                    Vector3f(),
                    Quaternionf(),
                    Vector3f(BANNER_SCALE, BANNER_SCALE, BANNER_SCALE),
                    Quaternionf(),
                )
            )
            // view_range is a multiplier, not block distance: actual range =
            // view_range × entity_distance_scaling × 64. 32 would render the banner
            // from ~2000 blocks away; 1.5 (~96 blocks) is plenty for a head-mounted flag.
            d.setViewRange(1.5f)
            d.persistentDataContainer.set(tagKey, PersistentDataType.STRING, player.uniqueId.toString())
        }
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
     * to clean up orphans left behind by a server crash (or by older passenger-based builds
     * that dropped displays on teleport).
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
}
