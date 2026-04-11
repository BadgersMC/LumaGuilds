package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a guild's home location is set or updated by a member.
 */
class GuildHomeSetEvent(
    val guildId: UUID,
    val playerId: UUID
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
