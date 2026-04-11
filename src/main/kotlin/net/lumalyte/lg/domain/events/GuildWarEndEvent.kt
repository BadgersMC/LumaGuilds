package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a guild war ends, either by victory, surrender, or expiration.
 */
class GuildWarEndEvent(
    val warId: UUID,
    val winnerGuildId: UUID?,
    val loserGuildId: UUID?,
    val declaringGuildId: UUID,
    val defendingGuildId: UUID
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
