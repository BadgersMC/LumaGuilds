package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/**
 * Closes any open guild menus for members still online when their guild is disbanded,
 * and removes all chat channels (Parties) associated with the guild.
 */
class GuildDisbandedListener(
    private val partyService: PartyService
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildDisbandedListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        // 1. Close inventories and notify online members
        event.memberIds.forEach { memberId ->
            val player = Bukkit.getPlayer(memberId) ?: return@forEach
            if (!player.isOnline) return@forEach
            player.closeInventory()
            player.sendMessage("§c✗ Your guild §f${event.guild.name} §chas been disbanded.")
        }

        // 2. Remove all Party channels associated with this guild
        try {
            val parties = partyService.getActivePartiesForGuild(event.guild.id)
            parties.forEach { party ->
                if (partyService.deleteParty(party.id)) {
                    logger.info("Removed guild channel ${party.name} (${party.id}) for disbanded guild ${event.guild.name}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up channels for disbanded guild ${event.guild.name}", e)
        }
    }
}