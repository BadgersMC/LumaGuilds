package net.lumalyte.lg.domain.values

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberPlaytimeLedgerTest {
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val cara = UUID.randomUUID()

    @Test fun `stored offline values are summed without reading anyone`() {
        val ledger = MemberPlaytimeLedger(MemberPlaytimeStore.InMemory(mutableMapOf(alice to 100L, bob to 200L)))
        assertEquals(listOf(100L, 200L), ledger.ticks(listOf(alice, bob)) { null })
        assertEquals(0, ledger.pending)
    }

    @Test fun `an online member is read live and an unknown member holds the total until read`() {
        val store = MemberPlaytimeStore.InMemory(mutableMapOf(alice to 100L))
        val ledger = MemberPlaytimeLedger(store)
        assertNull(ledger.ticks(listOf(alice, bob, cara)) { if (it == cara) 50L else null })
        assertEquals(1, ledger.pending)
        val reads = mutableListOf<UUID>()
        assertEquals(1, ledger.drain({ reads += it; 300L }, budgetNanos = Long.MAX_VALUE, maxReads = 10))
        assertEquals(listOf(bob), reads)
        assertEquals(listOf(100L, 300L, 50L), ledger.ticks(listOf(alice, bob, cara)) { if (it == cara) 50L else null })
        assertEquals(300L, store.loadAll()[bob])
    }

    @Test fun `a guild waiting on a member is read before the startup backlog`() {
        val ledger = MemberPlaytimeLedger(MemberPlaytimeStore.InMemory())
        ledger.enqueue(listOf(alice, bob))
        ledger.ticks(listOf(cara)) { null }
        val reads = mutableListOf<UUID>()
        ledger.drain({ reads += it; 1L }, budgetNanos = Long.MAX_VALUE, maxReads = 10)
        assertEquals(listOf(cara, alice, bob), reads)
    }

    @Test fun `drain stops at its time budget but always makes progress`() {
        val ledger = MemberPlaytimeLedger(MemberPlaytimeStore.InMemory())
        ledger.enqueue(List(10) { UUID.randomUUID() })
        var now = 0L
        val done = ledger.drain({ now += 1_500_000L; 1L }, budgetNanos = 2_000_000L, maxReads = 4, nanoTime = { now })
        assertEquals(2, done)
        assertEquals(8, ledger.pending)
        assertEquals(1, ledger.drain({ now += 5_000_000L; 1L }, budgetNanos = 2_000_000L, maxReads = 4, nanoTime = { now }))
    }

    @Test fun `recording the same value again does not save`() {
        var saves = 0
        val store = object : MemberPlaytimeStore {
            override fun loadAll() = mapOf(alice to 10L)
            override fun save(playerId: UUID, ticks: Long) { saves++ }
        }
        val ledger = MemberPlaytimeLedger(store)
        ledger.record(alice, 10L)
        ledger.record(alice, 20L)
        assertEquals(1, saves)
    }

    @Test fun `a failed read is dropped and requested again by the next total`() {
        val ledger = MemberPlaytimeLedger(MemberPlaytimeStore.InMemory())
        ledger.enqueue(listOf(alice))
        ledger.drain({ error("stats unreadable") }, budgetNanos = Long.MAX_VALUE, maxReads = 4)
        assertEquals(0, ledger.pending)
        assertNull(ledger.ticks(listOf(alice)) { null })
        assertEquals(1, ledger.pending)
    }
}
