package net.lumalyte.lg.application.services

import java.util.UUID

/**
 * REQ-182: toggling one visiting rank. `null` means "every rank" (no pick stored);
 * toggling from there starts a pick of every rank but that one, and a pick that
 * covers every rank again collapses back to `null`, so new ranks stay admitted.
 */
object AllyHomeRankPicks {
    fun toggle(current: Set<UUID>?, allRanks: Set<UUID>, rank: UUID): Set<UUID>? {
        val base = current ?: allRanks
        val next = if (rank in base) base - rank else base + rank
        return if (next.containsAll(allRanks)) null else next
    }

    fun isAllowed(current: Set<UUID>?, rank: UUID): Boolean = current?.contains(rank) ?: true
}
