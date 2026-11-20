package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.time.Instant
import java.util.UUID

/**
 * Fired once, on the server thread, after a guild prestige newly commits (REQ-153).
 * Receipt replays, rejections and failures never fire it. Listeners cannot cancel or
 * change the prestige; exceptions are contained by the caller.
 */
class GuildPrestigeEvent(
    val guildId: UUID,
    val actorId: UUID,
    /** Resulting prestige tier, 1..6. */
    val tier: Int,
    val retainedRewardId: String,
    val appliedAt: Instant,
    /** True when this guild is the first on the server to reach [tier]. */
    val firstToReachTier: Boolean,
) : Event() {
    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
