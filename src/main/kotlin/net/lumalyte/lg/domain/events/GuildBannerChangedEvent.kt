package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Fired after the active public banner for a guild is set or removed. */
class GuildBannerChangedEvent(
    val guildId: UUID,
    val hasActiveBanner: Boolean,
) : Event() {
    override fun getHandlers(): HandlerList = Companion.handlers

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}
