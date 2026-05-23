package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

/**
 * Every 5 ticks: keep each tracked player's bannerman display following the player
 * and toggle visibility based on elytra / invisibility state. 5 ticks (~0.25s) is fast
 * enough to look attached without flooding the scheduler with sub-block teleports while
 * players are walking.
 */
internal class BannermanTickTask(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService
) : BukkitRunnable() {

    companion object {
        private const val TICK_PERIOD = 5L
        // ~0.5 block squared: don't re-teleport for sub-block jitter while walking.
        private const val REPOSITION_THRESHOLD_SQ = 0.25
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

        val shouldShow = BannermanVisibility.shouldShow(
            hasElytra = isWearingElytra(player),
            hasInvisibility = player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        )
        display.isVisibleByDefault = shouldShow

        val target = player.location.clone()
        target.y += 1.0
        target.yaw = player.location.yaw
        target.pitch = 0f
        if (display.world != player.world || display.location.distanceSquared(target) > REPOSITION_THRESHOLD_SQ) {
            display.teleport(target)
        }
    }

    private fun isWearingElytra(player: Player): Boolean =
        player.inventory.chestplate?.type == Material.ELYTRA
}
