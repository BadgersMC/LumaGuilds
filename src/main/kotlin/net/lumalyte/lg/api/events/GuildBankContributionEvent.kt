package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** A confirmed player deposit notification. Consumers must verify its canonical receipt, never a gross amount. */
class GuildBankContributionEvent(
    val guildId: UUID,
    val playerId: UUID,
    val transactionId: UUID,
) : Event() {
    companion object {
        @JvmStatic private val handlers = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
