package net.lumalyte.lg.interaction.menus

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/**
 * REQ-266: reads a menu's data off the main thread, then shows it on the main thread if the
 * player is still online and still on the same screen. Without a registered loader (unit tests)
 * the read runs in place.
 */
open class MenuLoader {
    open fun <T> load(player: Player, stillCurrent: () -> Boolean, read: () -> T, show: (T) -> Unit) {
        show(read())
    }

    companion object {
        val Immediate = MenuLoader()
    }
}

class BukkitMenuLoader(private val plugin: Plugin) : MenuLoader() {
    private val logger = LoggerFactory.getLogger(BukkitMenuLoader::class.java)

    override fun <T> load(player: Player, stillCurrent: () -> Boolean, read: () -> T, show: (T) -> Unit) {
        if (!plugin.isEnabled) return
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val result = runCatching(read)
            result.exceptionOrNull()?.let { logger.warn("Menu read failed for ${player.name}", it) }
            if (!plugin.isEnabled) return@Runnable
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline && stillCurrent()) result.onSuccess(show)
            })
        })
    }
}
