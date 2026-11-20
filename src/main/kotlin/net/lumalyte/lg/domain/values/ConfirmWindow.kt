package net.lumalyte.lg.domain.values

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * REQ-214: a player confirms a risky command by running it again within [windowMs].
 * The first run returns false and starts the window; the second run of the same action returns true.
 */
class ConfirmWindow(private val windowMs: Long, private val now: () -> Long = System::currentTimeMillis) {
    private val pending = ConcurrentHashMap<UUID, Pair<String, Long>>()

    fun confirm(playerId: UUID, action: String): Boolean {
        val time = now()
        val previous = pending.remove(playerId)
        if (previous != null && previous.first == action && time - previous.second <= windowMs) return true
        pending[playerId] = action to time
        return false
    }
}
