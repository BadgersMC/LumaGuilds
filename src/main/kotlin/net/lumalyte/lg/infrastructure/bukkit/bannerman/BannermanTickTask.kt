package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

/**
 * Position and rotation are driven here every tick — the display is NOT mounted on
 * the player, so nothing tracks it automatically:
 *   - teleport it to just above/behind the player's eye (head position)
 *   - rotate it to the player's view yaw AND pitch so the banner tracks the head
 *     bone exactly like a worn helmet-slot item
 *   - visibility toggle for invisibility (only re-applied if it changed, since
 *     isVisibleByDefault triggers tracker resends)
 *
 * Client-side interpolation (teleport + display interpolation durations set at spawn)
 * smooths the per-tick updates.
 */
internal class BannermanTickTask(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService
) : BukkitRunnable() {

    companion object {
        private const val TICK_PERIOD = 1L
    }

    fun start() {
        runTaskTimer(plugin, 0L, TICK_PERIOD)
    }

    override fun run() {
        for (player in Bukkit.getOnlinePlayers()) {
            updatePlayerBannerman(player)
        }
    }

    private fun updatePlayerBannerman(player: Player) {
        if (!renderer.isTracking(player.uniqueId)) return
        val display = renderer.currentDisplay(player.uniqueId) ?: return

        // Safety net for any teleport path that slipped past the listener — an entity
        // cannot change worlds, so a stale display must be dropped (the teleport
        // listener respawns it in the destination world).
        if (display.world != player.world) {
            renderer.despawnFor(player.uniqueId)
            return
        }

        display.teleport(BannermanPosition.headPosition(player.eyeLocation, player.pose))
        // Upright poses: banner tracks the head bone (view yaw + view pitch), tilting
        // back on look-up and forward on look-down like a worn helmet-slot item.
        // Horizontal poses (elytra flight / swimming): the body lies along the view
        // direction, so pitch 90 lays the banner pole along the body axis instead of
        // keeping it world-vertical ("stuck pointing straight up while flying").
        val pose = player.pose
        val pitch = if (pose == Pose.FALL_FLYING || pose == Pose.SWIMMING) 90f else player.pitch
        display.setRotation(player.yaw, pitch)

        val shouldShow = BannermanVisibility.shouldShow(
            hasInvisibility = player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        )
        if (display.isVisibleByDefault != shouldShow) {
            display.isVisibleByDefault = shouldShow
        }
    }
}
