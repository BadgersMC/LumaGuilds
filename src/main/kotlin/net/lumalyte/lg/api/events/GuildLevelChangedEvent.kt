package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Fired after a guild's persisted progression level changes. */
internal class GuildLevelChangedEvent(
    /** Guild whose persisted progression level changed. */
    val guildId: UUID,
    /** New persisted progression level. */
    val newLevel: Int,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    /** Bukkit's shared handler list for this event. */
    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        /** Returns the shared handler list to Bukkit. */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
