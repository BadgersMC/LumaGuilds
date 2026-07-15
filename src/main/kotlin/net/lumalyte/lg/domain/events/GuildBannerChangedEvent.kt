package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Fired after the active public banner for a guild is set or removed. */
@Suppress("LibraryEntitiesShouldNotBePublic") // Cross-plugin listeners require a public Bukkit event type.
class GuildBannerChangedEvent(
    /** Guild whose active banner changed. */
    val guildId: UUID,
    /** Whether the guild now has an active banner. */
    val hasActiveBanner: Boolean,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    /** Bukkit handler-list holder for this event type. */
    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        /** Returns the shared Bukkit handler list. */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
