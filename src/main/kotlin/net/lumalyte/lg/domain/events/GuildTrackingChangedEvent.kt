package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Event fired when a guild's Lunar Client tracking setting is toggled.
 * Listeners (e.g. Apollo GuildTeamService) should react immediately rather
 * than waiting for the next scheduled refresh.
 */
class GuildTrackingChangedEvent(
    val guildId: UUID,
    val enabled: Boolean
) : Event() {

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
