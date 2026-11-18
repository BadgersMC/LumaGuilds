package net.lumalyte.lg.domain.values

import java.util.UUID

/** Where the ledger keeps each player's last known vanilla play ticks between restarts. */
interface MemberPlaytimeStore {
    fun loadAll(): Map<UUID, Long>
    fun save(playerId: UUID, ticks: Long)

    /** Keeps values for this run only (tests, or no database). */
    class InMemory(private val values: MutableMap<UUID, Long> = HashMap()) : MemberPlaytimeStore {
        override fun loadAll(): Map<UUID, Long> = values.toMap()
        override fun save(playerId: UUID, ticks: Long) { values[playerId] = ticks }
    }
}

/**
 * REQ-300: each player's last known vanilla play ticks, so a guild's combined playtime is a sum of
 * stored numbers rather than a read of every offline member's statistics file. Online members are
 * read live (cheap, already in memory); offline members use the value stored when they last left.
 * Members with no stored value yet are read later, a few at a time, by [drain].
 *
 * Not thread-safe: the caller keeps every call on one thread (the server thread).
 */
class MemberPlaytimeLedger(private val store: MemberPlaytimeStore) {
    private val known = HashMap<UUID, Long>(store.loadAll())
    /** Members a guild total is waiting on; read before [backlog]. */
    private val urgent = LinkedHashSet<UUID>()
    private val backlog = LinkedHashSet<UUID>()

    val pending: Int get() = urgent.size + backlog.size

    fun known(playerId: UUID): Long? = known[playerId]

    /** Queues players with no stored value for a later [drain], behind any a guild is waiting on. */
    fun enqueue(players: Collection<UUID>) {
        players.forEach { if (it !in known && it !in urgent) backlog += it }
    }

    /**
     * Each roster member's play ticks, or null while any member's value is still unknown. Unknown
     * members are moved to the front of the read queue. [live] gives an online member's current ticks
     * (null when offline); those refresh the stored value in memory only, so it is saved when they leave.
     */
    fun ticks(roster: Collection<UUID>, live: (UUID) -> Long?): List<Long>? {
        val values = ArrayList<Long>(roster.size)
        var complete = true
        for (id in roster) {
            val ticks = live(id)?.also { known[id] = it.coerceAtLeast(0L) } ?: known[id]
            if (ticks == null) {
                complete = false
                backlog -= id
                urgent += id
            } else {
                values += ticks.coerceAtLeast(0L)
            }
        }
        return values.takeIf { complete }
    }

    /** Stores [ticks] as [playerId]'s current playtime, e.g. when they join or leave. */
    fun record(playerId: UUID, ticks: Long) {
        val value = ticks.coerceAtLeast(0L)
        urgent -= playerId
        backlog -= playerId
        if (known.put(playerId, value) != value) store.save(playerId, value)
    }

    /**
     * Reads queued players with [read] until [budgetNanos] has passed (measured with [nanoTime]) or
     * [maxReads] are done; always reads at least one when any are queued. A failed read is dropped
     * from the queue and asked for again by the next guild total that needs it. Returns reads done.
     */
    fun drain(read: (UUID) -> Long, budgetNanos: Long, maxReads: Int, nanoTime: () -> Long = System::nanoTime): Int {
        val start = nanoTime()
        var done = 0
        while (done < maxReads) {
            val id = (urgent.firstOrNull() ?: backlog.firstOrNull()) ?: break
            urgent -= id
            backlog -= id
            runCatching { read(id) }.onSuccess { record(id, it) }
            done++
            if (nanoTime() - start >= budgetNanos) break
        }
        return done
    }
}
