package net.lumalyte.lg.infrastructure.listeners

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * REQ-265: closes the place/break XP loop. When a player breaks a block a player placed, they get
 * the block back, so one placement of that material by the same player within [windowMillis] is a
 * re-placement and earns no place XP. Credits are per player and material, capped, and expire.
 */
class PlaceRefundLedger(
    private val windowMillis: Long = 30 * 60_000L,
    private val cap: Int = 2304,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val player: UUID, val material: String)
    private data class Entry(val count: Int, val at: Long)

    private val entries = ConcurrentHashMap<Key, Entry>()

    /** A player broke a player-placed [material] block and got it back. */
    fun recordPickup(player: UUID, material: String) {
        val now = clock()
        entries.compute(Key(player, material)) { _, old ->
            val live = old?.takeIf { now - it.at <= windowMillis }?.count ?: 0
            Entry((live + 1).coerceAtMost(cap), now)
        }
    }

    /** True when this placement uses up a recent pickup, so it earns no XP. */
    fun consume(player: UUID, material: String): Boolean {
        val now = clock()
        var used = false
        entries.computeIfPresent(Key(player, material)) { _, old ->
            if (now - old.at > windowMillis) return@computeIfPresent null
            used = true
            if (old.count <= 1) null else old.copy(count = old.count - 1)
        }
        return used
    }

    /** Drops expired credits; kept across logouts so relogging doesn't reset the loop guard. */
    fun prune() {
        val now = clock()
        entries.entries.removeIf { now - it.value.at > windowMillis }
    }
}
