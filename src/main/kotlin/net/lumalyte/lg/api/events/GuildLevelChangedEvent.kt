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
    override fun getHandlers(): HandlerList = HANDLERS

    /** Bukkit's shared handler list for this event. */
    companion object {
        /** Shared handler list required by Bukkit. */
        @JvmStatic
        private val HANDLERS = HandlerList()

        /** Returns the shared handler list to Bukkit. */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
