package net.lumalyte.lg.api.events

import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

class GuildRenamedEvent(
    val oldGuild: Guild,
    val guild: Guild,
    val actorId: UUID,
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
    override fun getHandlers(): HandlerList = HANDLERS
}
