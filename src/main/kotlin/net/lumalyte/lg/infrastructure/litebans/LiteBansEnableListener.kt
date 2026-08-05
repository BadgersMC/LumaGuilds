package net.lumalyte.lg.infrastructure.litebans

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/**
 * Waits for LiteBans to finish enabling before wiring the Guild Strikes hook.
 *
 * LumaGuilds' own onEnable can run BEFORE LiteBans' onEnable (plugin load order
 * is not guaranteed for softdependencies), and `litebans.api.Events.get()`
 * returns null until LiteBans has enabled. Registering the hook too early
 * silently fails — observed on the Fuji test server: LumaGuilds enabled at
 * T+0, LiteBans at T+22s, hook never wired.
 *
 * This listener fires on PluginEnableEvent for LiteBans and calls
 * [onLiteBansEnabled]. The caller should also attempt registration once
 * immediately, covering the reverse load order (LiteBans already enabled).
 */
class LiteBansEnableListener(
    private val onLiteBansEnabled: () -> Unit,
) : Listener {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name != "LiteBans") return
        logger.info("LiteBans enabled — wiring Guild Strikes hook now.")
        onLiteBansEnabled()
    }

    /** Convenience: only wire when the plugin actually exists in the server. */
    companion object {
        fun liteBansPresent(server: org.bukkit.Server): Boolean =
            server.pluginManager.getPlugin("LiteBans") != null

        /** Null-safe LiteBans presence check for a plugin reference. */
        fun isLiteBans(plugin: Plugin): Boolean = plugin.name == "LiteBans"
    }
}
