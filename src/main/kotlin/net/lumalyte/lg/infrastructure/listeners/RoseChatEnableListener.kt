package net.lumalyte.lg.infrastructure.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.slf4j.LoggerFactory

/**
 * Waits for RoseChat to finish enabling before wiring the guild/ally/modchat
 * channel provider.
 *
 * LumaGuilds' own onEnable can run BEFORE RoseChat's onEnable even with
 * `depend: [RoseChat]` (observed on the Fuji test server: LumaGuilds enabled
 * at T+0, RoseChat ~4 minutes later). At that point
 * `isPluginEnabled("RoseChat")` is false, so the guarded registration in
 * onEnable would silently never run and the guild channels in channels.yml
 * would fail to load ("Attempted to load LumaGuilds channel ... but LumaGuilds
 * is not installed!").
 *
 * This listener fires on PluginEnableEvent for RoseChat and calls
 * [onRoseChatEnabled]. The caller should also attempt registration once
 * immediately, covering the normal load order (RoseChat already enabled).
 */
class RoseChatEnableListener(
    private val onRoseChatEnabled: () -> Unit,
) : Listener {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name != "RoseChat") return
        logger.info("RoseChat enabled — wiring LumaGuilds channel provider now.")
        onRoseChatEnabled()
    }
}
