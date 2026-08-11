package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * REQ-009: `bank.max_bank_balance` must act as the hard ceiling on deposits
 * (progression level limits refine it downward), and suspicious-transaction
 * auto-lock must respect `bank.suspicious_transaction_threshold` +
 * `bank.auto_lock_suspicious_accounts`.
 */
class BankConfigEnforcementTest {

    @Test
    fun `config cap applies when no progression limit exists`() {
        assertEquals(1_000_000, BankServiceBukkit.effectiveMaxBalance(1_000_000, null))
    }

    @Test
    fun `progression limit refines the config cap downward`() {
        assertEquals(100_000, BankServiceBukkit.effectiveMaxBalance(1_000_000, 100_000))
    }

    @Test
    fun `config cap wins when progression limit exceeds it`() {
        assertEquals(1_000_000, BankServiceBukkit.effectiveMaxBalance(1_000_000, 2_000_000))
    }

    @Test
    fun `auto-lock triggers at or above threshold when enabled`() {
        assertTrue(BankServiceBukkit.shouldAutoLock(50_000, 50_000, true))
        assertTrue(BankServiceBukkit.shouldAutoLock(75_000, 50_000, true))
    }

    @Test
    fun `auto-lock does not trigger below threshold`() {
        assertFalse(BankServiceBukkit.shouldAutoLock(49_999, 50_000, true))
    }

    @Test
    fun `auto-lock never triggers when disabled`() {
        assertFalse(BankServiceBukkit.shouldAutoLock(500_000, 50_000, false))
    }
}
