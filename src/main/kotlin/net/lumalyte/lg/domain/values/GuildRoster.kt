package net.lumalyte.lg.domain.values

import java.util.UUID

/** REQ-191: one member on the guild page roster. Lower [rankPriority] is a higher rank. */
data class RosterEntry(val playerId: UUID, val name: String, val rankPriority: Int, val online: Boolean)

/** REQ-191: roster order, cycled by the sort button. */
enum class RosterSort {
    RANK, ONLINE, NAME;

    fun next(): RosterSort = entries[(ordinal + 1) % entries.size]
}

/** REQ-191: ordering and paging of the guild page roster. */
object GuildRoster {
    const val PAGE_SIZE = 36

    private val byName = compareBy<RosterEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    private val byRank = compareBy<RosterEntry> { it.rankPriority }.then(byName)

    fun order(entries: Collection<RosterEntry>, sort: RosterSort): List<RosterEntry> = when (sort) {
        RosterSort.RANK -> entries.sortedWith(byRank)
        RosterSort.ONLINE -> entries.sortedWith(compareBy<RosterEntry> { !it.online }.then(byRank))
        RosterSort.NAME -> entries.sortedWith(byName)
    }

    fun pageCount(size: Int): Int = maxOf(1, (size + PAGE_SIZE - 1) / PAGE_SIZE)

    /** The heads on [page], clamped to the first and last page. */
    fun page(ordered: List<RosterEntry>, page: Int): List<RosterEntry> {
        val clamped = page.coerceIn(0, pageCount(ordered.size) - 1)
        return ordered.drop(clamped * PAGE_SIZE).take(PAGE_SIZE)
    }
}

/** REQ-192: how long ago a member was last online, in the largest whole unit (at least 1 minute). */
data class LastSeen(val unit: Unit, val amount: Long) {
    enum class Unit { MINUTES, HOURS, DAYS }

    companion object {
        /** Null when the player was never seen ([lastMs] 0 or less). */
        fun of(nowMs: Long, lastMs: Long): LastSeen? {
            if (lastMs <= 0) return null
            val minutes = maxOf(1, (nowMs - lastMs) / 60_000)
            return when {
                minutes < 60 -> LastSeen(Unit.MINUTES, minutes)
                minutes < 60 * 24 -> LastSeen(Unit.HOURS, minutes / 60)
                else -> LastSeen(Unit.DAYS, minutes / (60 * 24))
            }
        }
    }
}
