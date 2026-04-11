package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Event fired when a player kills a rival guild member during an active war.
 */
class GuildWarKillEvent(
    val warId: UUID,
    val killerId: UUID,
    val victimId: UUID,
    val killerGuildId: UUID,
    val victimGuildId: UUID
) : Event() {

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = Companion.handlers
}
