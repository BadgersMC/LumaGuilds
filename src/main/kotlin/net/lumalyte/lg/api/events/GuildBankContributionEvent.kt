package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** A confirmed deposit notification whose canonical receipt must be verified by consumers. */
@Suppress("LibraryEntitiesShouldNotBePublic") // Bukkit listeners require a public event type.
class GuildBankContributionEvent(
    /** Guild that received the deposit. */
    val guildId: UUID,
    /** Player who made the confirmed contribution. */
    val playerId: UUID,
    /** Canonical gold operation identity; no unverified gross amount is supplied. */
    val transactionId: UUID,
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
