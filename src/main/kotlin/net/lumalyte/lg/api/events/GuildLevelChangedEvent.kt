package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Fired after a guild's persisted progression level changes. */
class GuildLevelChangedEvent(
    val guildId: UUID,
    val newLevel: Int,
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = Companion.handlers
}
