package net.lumalyte.lg.infrastructure.listeners.apollo

import net.lumalyte.lg.domain.events.GuildMemberJoinEvent
import net.lumalyte.lg.infrastructure.services.apollo.GuildRichPresenceService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.slf4j.LoggerFactory

/**
 * Listens for guild-related events to update Apollo Rich Presence.
 */
class GuildRichPresenceListener(
    private val richPresenceService: GuildRichPresenceService
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildRichPresenceListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        try {
            // Update rich presence when player joins server
            richPresenceService.updateGuildRichPresence(event.player)
        } catch (e: Exception) {
            logger.debug("Error updating rich presence on join: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        try {
            val player = Bukkit.getPlayer(event.playerId) ?: return
            richPresenceService.onPlayerJoinGuild(player)
        } catch (e: Exception) {
            logger.debug("Error updating rich presence on guild join: ${e.message}")
        }
    }
}
