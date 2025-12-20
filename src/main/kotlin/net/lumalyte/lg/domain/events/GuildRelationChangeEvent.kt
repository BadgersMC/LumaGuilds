package net.lumalyte.lg.domain.events

import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Event fired when a guild relation changes status.
 * This includes: alliance acceptance, war declaration, truce acceptance, unenemy acceptance.
 */
class GuildRelationChangeEvent(
    val guild1: UUID,
    val guild2: UUID,
    val newRelationType: RelationType,
    val relation: Relation
) : Event() {

    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }

    override fun getHandlers(): HandlerList {
        return Companion.handlers
    }
}
