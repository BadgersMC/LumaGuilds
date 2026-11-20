package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.KillService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/** REQ-199: saves each player kill to the kill log, off the main thread (guild lookups hit the database). */
class GuildKillLogListener(private val plugin: Plugin, private val kills: KillService) : Listener {
    private val logger = LoggerFactory.getLogger(GuildKillLogListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer as? Player ?: return
        if (killer.uniqueId == victim.uniqueId) return
        val killerId = killer.uniqueId
        val victimId = victim.uniqueId
        val weapon = killer.inventory.itemInMainHand.type.takeUnless { it.isAir }?.name
        val worldId = victim.world.uid
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            runCatching { kills.recordKill(killerId, victimId, weapon, worldId, null) }
                .onFailure { logger.error("Unable to record kill of $victimId by $killerId", it) }
        })
    }
}
