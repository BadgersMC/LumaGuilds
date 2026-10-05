package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.entity.Player

/**
 * Public integration event for one externally-managed exploration milestone.
 *
 * Producers should fire this only when a player crosses from incomplete to
 * complete. LumaGuilds owns XP eligibility, cap and guild-membership checks.
 */
class GuildExplorationMilestoneEvent(
    val player: Player,
    val provider: String,
    val milestoneId: String,
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = Companion.handlers
}
