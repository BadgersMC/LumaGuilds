package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when ownership of a guild is transferred from one player to another.
 */
class GuildOwnershipTransferEvent(
    val guildId: UUID,
    val oldOwnerId: UUID,
    val newOwnerId: UUID
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
