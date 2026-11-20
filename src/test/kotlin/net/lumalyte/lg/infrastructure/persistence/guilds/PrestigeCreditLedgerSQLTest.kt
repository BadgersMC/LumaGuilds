package net.lumalyte.lg.infrastructure.persistence.guilds

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-154: one durable row per member/prestige pair, surviving reopen. */
class PrestigeCreditLedgerSQLTest : RewardSqlTestFixture() {
    private val player = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    @Test
    fun `records each pair once and reads it back after reopen`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val ledger = PrestigeCreditLedgerSQL(storage)
        assertEquals(emptySet(), ledger.delivered(player))
        assertTrue(ledger.record(player, first, Instant.ofEpochMilli(1_000)))
        assertFalse(ledger.record(player, first, Instant.ofEpochMilli(2_000)), "duplicate pair must be ignored")
        assertTrue(ledger.record(player, second, Instant.ofEpochMilli(3_000)))
        assertTrue(ledger.record(other, first, Instant.ofEpochMilli(4_000)))
        closeStorage(storage)

        val reopened = openStorage()
        val again = PrestigeCreditLedgerSQL(reopened)
        assertEquals(setOf(first, second), again.delivered(player))
        assertEquals(setOf(first), again.delivered(other))
    }

    // REQ-187: staff can make a guild's prestiges creditable again.
    @Test
    fun `forgetting prestiges clears them for every member`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val ledger = PrestigeCreditLedgerSQL(storage)
        ledger.record(player, first, Instant.ofEpochMilli(1_000))
        ledger.record(other, first, Instant.ofEpochMilli(1_000))
        ledger.record(player, second, Instant.ofEpochMilli(1_000))
        assertEquals(2, ledger.forget(setOf(first)))
        assertEquals(setOf(second), ledger.delivered(player))
        assertEquals(emptySet(), ledger.delivered(other))
        assertEquals(0, ledger.forget(emptySet()))
    }
}
