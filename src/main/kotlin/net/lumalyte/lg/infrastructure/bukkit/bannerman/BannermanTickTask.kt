package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Position and rotation are driven here every tick (the server's max update rate) —
 * the display is NOT mounted on the player, so nothing tracks it automatically:
 *   - teleport it to the head position
 *   - rotate it to the player's view yaw and pitch (head-bone tracking)
 *   - despawn while the body is horizontal (swim / elytra flight): orienting the
 *     banner along the body was too buggy, so it simply hides and respawns when the
 *     player returns to an upright pose
 *   - visibility toggle for invisibility (only re-applied if it changed, since
 *     isVisibleByDefault triggers tracker resends)
 *
 * No client-side interpolation durations are set at spawn, so each tick's update
 * applies immediately instead of lagging a tick behind the head.
 */
internal class BannermanTickTask(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService,
    private val respawn: (Player) -> Unit,
) : BukkitRunnable() {

    companion object {
        private const val TICK_PERIOD = 1L
        private val HORIZONTAL_BODY_POSES = setOf(Pose.SWIMMING, Pose.FALL_FLYING)
    }

    /** Players whose banner was despawned for a horizontal pose, awaiting upright respawn. */
    private val hiddenWhileHorizontal: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun start() {
        runTaskTimer(plugin, 0L, TICK_PERIOD)
    }

    override fun run() {
        for (player in Bukkit.getOnlinePlayers()) {
            updatePlayerBannerman(player)
        }
        // Prune UUIDs of players who disconnected while horizontal (they never get an
        // upright tick to remove themselves).
        if (hiddenWhileHorizontal.isNotEmpty()) {
            hiddenWhileHorizontal.removeIf { Bukkit.getPlayer(it) == null }
        }
    }

    private fun updatePlayerBannerman(player: Player) {
        if (player.pose in HORIZONTAL_BODY_POSES) {
            // Only track players who actually had a display — avoids recording every
            // swimmer/glider and the pointless guild lookup on their next upright tick.
            if (renderer.isTracking(player.uniqueId)) {
                renderer.despawnFor(player.uniqueId)
                hiddenWhileHorizontal.add(player.uniqueId)
            }
            return
        }

        if (hiddenWhileHorizontal.remove(player.uniqueId)) {
            respawn(player)
        }
        if (!renderer.isTracking(player.uniqueId)) return
        val display = renderer.currentDisplay(player.uniqueId) ?: return

        // Safety net for any teleport path that slipped past the listener — an entity
        // cannot change worlds, so a stale display must be dropped and respawned in
        // the destination world (without the respawn the banner would be gone forever).
        if (display.world != player.world) {
            renderer.despawnFor(player.uniqueId)
            respawn(player)
            return
        }

        display.teleport(BannermanPosition.headPosition(player.eyeLocation, player.pose))
        display.setRotation(player.yaw, player.pitch)

        val shouldShow = BannermanVisibility.shouldShow(
            hasInvisibility = player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        )
        if (display.isVisibleByDefault != shouldShow) {
            display.isVisibleByDefault = shouldShow
        }
    }
}
