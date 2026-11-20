package net.lumalyte.lg.domain.rewards

import java.time.Instant
import java.util.UUID

/** REQ-275: one guild's place in the Hall: its prestige count and when it reached that count. */
data class HallStanding(val guildId: UUID, val prestige: Int, val reachedAt: Instant)

/** REQ-275: a statue spot placed by staff. Slot 1 holds the top guild. */
data class HallPedestal(val slot: Int, val world: String, val x: Double, val y: Double, val z: Double, val yaw: Float)

/** REQ-275: who stands in the Hall. Highest prestige first; a tie goes to whoever got there first. */
object HallRanking {
    fun rank(standings: List<HallStanding>, slots: Int): List<HallStanding> =
        standings.filter { it.prestige > 0 }
            .sortedWith(compareByDescending<HallStanding> { it.prestige }.thenBy { it.reachedAt }.thenBy { it.guildId.toString() })
            .take(slots.coerceAtLeast(0))

    /** Pedestals in slot order paired with the guild that stands on each; spare pedestals stay empty. */
    fun assign(pedestals: List<HallPedestal>, standings: List<HallStanding>): List<Pair<HallPedestal, HallStanding?>> {
        val ordered = pedestals.sortedBy { it.slot }
        val ranked = rank(standings, ordered.size)
        return ordered.mapIndexed { index, pedestal -> pedestal to ranked.getOrNull(index) }
    }
}
