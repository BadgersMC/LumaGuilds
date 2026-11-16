package net.lumalyte.lg.infrastructure.services.hall

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REQ-276: protection plugins cancel plugin spawns of living entities in regions that deny mob
 * spawning (WorldGuard's `block-plugin-spawning`), which removed the Hall statue's mannequin the
 * moment it spawned. Only for the statues LumaGuilds is spawning right now, this lets the spawn
 * through after every other plugin has had its say. Nothing else is touched.
 */
internal object HallSpawnGuard : Listener {
    private val ours = ConcurrentHashMap.newKeySet<UUID>()
    private val registered = AtomicBoolean(false)

    fun register(plugin: Plugin) {
        if (registered.compareAndSet(false, true)) plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Marks [id] as a statue being spawned; called inside the spawn's set-up, before it joins the world. */
    fun allow(id: UUID) { ours += id }

    fun done(id: UUID) { ours -= id }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun spawn(event: EntitySpawnEvent) {
        if (event.isCancelled && event.entity.uniqueId in ours) event.isCancelled = false
    }
}
