package net.lumalyte.lg.domain.events

import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Event fired when a guild is successfully disbanded.
 * Carries the dissolved guild and the UUIDs of every former member so that
 * listeners can notify or clean up on a per-member basis without needing a
 * live DB query (members are already removed when this fires).
 */
class GuildDisbandedEvent(
    val guild: Guild,
    val memberIds: Set<UUID>,
    val actorId: UUID
) : Event() {

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
