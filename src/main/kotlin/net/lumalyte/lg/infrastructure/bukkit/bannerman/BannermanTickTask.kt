package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

/**
 * Position and rotation are handled by passenger-mounting the display on the player
 * (see [BannermanRenderService.spawnFor]) — the vanilla client keeps them rigidly
 * attached, so no per-tick teleport is needed. This task only flips visibility based
 * on elytra / invisibility state, which doesn't need sub-second responsiveness.
 */
internal class BannermanTickTask(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService
) : BukkitRunnable() {

    companion object {
        private const val TICK_PERIOD = 20L
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

        display.isVisibleByDefault = BannermanVisibility.shouldShow(
            hasElytra = isWearingElytra(player),
            hasInvisibility = player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        )
    }

    private fun isWearingElytra(player: Player): Boolean =
        player.inventory.chestplate?.type == Material.ELYTRA
}
