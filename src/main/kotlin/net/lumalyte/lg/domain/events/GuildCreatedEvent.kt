package net.lumalyte.lg.domain.events

import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Event fired when a guild is successfully created.
 * This event allows various systems to react to guild creation without tight coupling.
 */
class GuildCreatedEvent(
    val guild: Guild,
    val ownerId: UUID
) : Event() {

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
}
