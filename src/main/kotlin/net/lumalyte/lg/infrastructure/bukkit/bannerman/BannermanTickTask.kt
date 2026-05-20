package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

/**
 * Every 2 ticks: keep each tracked player's bannerman display following the player
 * and toggle visibility based on elytra / invisibility state.
 */
class BannermanTickTask(
    private val plugin: JavaPlugin,
    private val renderer: BannermanRenderService
) : BukkitRunnable() {

    fun start() {
        runTaskTimer(plugin, 0L, 2L)
    }

    override fun run() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (!renderer.isTracking(player.uniqueId)) continue
            val display = renderer.currentDisplay(player.uniqueId) ?: continue

            val shouldShow = BannermanVisibility.shouldShow(
                hasElytra = isWearingElytra(player),
                hasInvisibility = player.hasPotionEffect(PotionEffectType.INVISIBILITY)
            )
            display.isVisibleByDefault = shouldShow

            val target = player.location.clone()
            target.y += 1.0
            target.yaw = player.location.yaw
            target.pitch = 0f
            if (display.world != player.world || display.location.distanceSquared(target) > 0.0001) {
                display.teleport(target)
            }
        }
    }

    private fun isWearingElytra(player: Player): Boolean =
        player.inventory.chestplate?.type == Material.ELYTRA
}
