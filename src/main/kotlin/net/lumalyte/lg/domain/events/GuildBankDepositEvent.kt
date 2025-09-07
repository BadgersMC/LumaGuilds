package net.lumalyte.lg.domain.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Event fired when a player deposits money into a guild bank
 */
class GuildBankDepositEvent(
    val guildId: UUID,
    val playerId: UUID,
    val amount: Int
) : Event() {
    
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = Companion.handlers
}
