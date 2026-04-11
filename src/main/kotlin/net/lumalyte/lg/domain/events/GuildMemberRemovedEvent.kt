package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a member is removed from a guild, either by leaving voluntarily or being kicked.
 */
class GuildMemberRemovedEvent(
    val guildId: UUID,
    val playerId: UUID,
    val actorId: UUID,
    val wasKicked: Boolean
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
