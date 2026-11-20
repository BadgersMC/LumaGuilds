package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.api.events.GuildPrestigeEvent
import net.lumalyte.lg.application.services.PrestigeDiscordRoleService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/** REQ-298: a guild prestige earns its members that tier's Discord role. */
class PrestigeDiscordRoleListener(private val service: PrestigeDiscordRoleService) : Listener {
    private val logger = LoggerFactory.getLogger(PrestigeDiscordRoleListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPrestige(event: GuildPrestigeEvent) {
        service.prestiged(event.guildId, event.tier, event.appliedAt).whenComplete { _, error ->
            if (error != null) logger.warn("Prestige Discord roles failed for guild ${event.guildId}", error.cause ?: error)
        }
    }
}
