package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/**
 * Closes any open guild menus for members still online when their guild is disbanded,
 * removes all chat channels (Parties) associated with the guild,
 * and despawns any bannerman displays attached to those members.
 */
internal class GuildDisbandedListener(
    private val partyService: PartyService,
    private val bannermanListeners: BannermanListeners,
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

        // 2. Remove this guild from every party it participates in.
        //    Single/last-guild parties are dissolved; multi-guild parties survive
        //    for the remaining guilds. Uses a system removal so the cleanup is not
        //    blocked by the actor's party-management permissions.
        try {
            val parties = partyService.getActivePartiesForGuild(event.guild.id)
            parties.forEach { party ->
                if (partyService.removeGuildFromPartyAsSystem(party.id, event.guild.id) == null) {
                    logger.info(
                        "Dissolved party channel ${party.name} (${party.id}) for disbanded guild ${event.guild.name}",
                    )
                } else {
                    logger.info(
                        "Removed disbanded guild ${event.guild.name} from party ${party.name} (${party.id})",
                    )
                }
            }
        } catch (e: IllegalStateException) {
            logger.error("Failed to clean up channels for disbanded guild ${event.guild.name}", e)
        }

        // 3. Despawn bannerman displays for every member (online or offline).
        bannermanListeners.onBannermanDisabled(event.guild.id)
    }
}
