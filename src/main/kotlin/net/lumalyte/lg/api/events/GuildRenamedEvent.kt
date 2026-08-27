package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

class GuildRenamedEvent(
    val guildId: UUID,
    val oldName: String,
    val newName: String,
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
    override fun getHandlers(): HandlerList = HANDLERS
}
