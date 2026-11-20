package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.api.events.GuildWarEndEvent
import net.lumalyte.lg.application.services.DiplomacyLog
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** REQ-203: war results go into both guilds' diplomacy history. */
class DiplomacyWarListener(private val log: DiplomacyLog) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWarEnd(event: GuildWarEndEvent) {
        log.warEnded(event.declaringGuildId, event.defendingGuildId, event.winnerGuildId)
    }
}
