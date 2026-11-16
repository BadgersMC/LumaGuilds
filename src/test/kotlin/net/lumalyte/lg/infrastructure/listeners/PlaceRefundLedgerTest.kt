package net.lumalyte.lg.infrastructure.listeners

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlaceRefundLedgerTest {
    private var now = 0L
    private val ledger = PlaceRefundLedger(windowMillis = 1_000, cap = 3, clock = { now })
    private val player = UUID.randomUUID()

    @Test
    fun `re-placing a block you just broke earns nothing, a fresh one still does`() {
        ledger.recordPickup(player, "STONE")
        assertTrue(ledger.consume(player, "STONE"))
        assertFalse(ledger.consume(player, "STONE"))
    }

    @Test
    fun `credits are per material and per player`() {
        ledger.recordPickup(player, "STONE")
        assertFalse(ledger.consume(player, "DIRT"))
        assertFalse(ledger.consume(UUID.randomUUID(), "STONE"))
    }

    @Test
    fun `credits expire and are capped`() {
        repeat(5) { ledger.recordPickup(player, "STONE") }
        repeat(3) { assertTrue(ledger.consume(player, "STONE")) }
        assertFalse(ledger.consume(player, "STONE"))
        ledger.recordPickup(player, "STONE")
        now += 2_000
        assertFalse(ledger.consume(player, "STONE"))
    }
}
