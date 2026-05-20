package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Closes any open guild menus for members still online when their guild is disbanded,
 * preventing stale GUIs from remaining open after the guild no longer exists.
 * Also despawns any bannerman displays attached to those members.
 */
class GuildDisbandedListener(
    private val bannermanListeners: BannermanListeners
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        event.memberIds.forEach { memberId ->
            val player = Bukkit.getPlayer(memberId) ?: return@forEach
            if (!player.isOnline) return@forEach
            player.closeInventory()
            player.sendMessage("§c✗ Your guild §f${event.guild.name} §chas been disbanded.")
        }
        // Despawn bannerman displays for every member (online or offline).
        bannermanListeners.onBannermanDisabled(event.guild.id)
    }
}
