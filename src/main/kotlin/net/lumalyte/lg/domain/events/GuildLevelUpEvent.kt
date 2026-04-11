package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a guild reaches a new level.
 */
class GuildLevelUpEvent(
    val guildId: UUID,
    val newLevel: Int
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
