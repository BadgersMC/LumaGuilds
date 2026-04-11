package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when one guild declares war on another guild.
 */
class GuildWarDeclaredEvent(
    val declaringGuildId: UUID,
    val defendingGuildId: UUID,
    val actorId: UUID
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
