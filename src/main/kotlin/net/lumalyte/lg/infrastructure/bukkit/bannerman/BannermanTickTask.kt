package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

/**
 * Position is handled by passenger-mounting the display on the player (see
 * [BannermanRenderService.spawnFor]) — the vanilla client keeps it rigidly attached.
 *
 * Per-tick work for tracked players:
 *   - Force body yaw to match head yaw. Vanilla lets a standing player swivel their head
 *     up to ~50° before the body catches up, which leaves a passenger banner facing the
 *     old direction. Locking body yaw to head yaw makes the banner track shoulder rotation
 *     in real time.
 *   - Visibility toggle for elytra / invisibility (only re-applied if it changed, since
 *     isVisibleByDefault triggers tracker resends).
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

        player.setBodyYaw(player.location.yaw)

        val shouldShow = BannermanVisibility.shouldShow(
            hasElytra = isWearingElytra(player),
            hasInvisibility = player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        )
        if (display.isVisibleByDefault != shouldShow) {
            display.isVisibleByDefault = shouldShow
        }
    }

    private fun isWearingElytra(player: Player): Boolean =
        player.inventory.chestplate?.type == Material.ELYTRA
}
