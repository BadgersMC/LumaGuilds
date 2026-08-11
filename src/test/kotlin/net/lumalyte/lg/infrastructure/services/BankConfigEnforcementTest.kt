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
    fun `progression loop yields null when no level grants a bankLimit - deposits stay open`() {
        // Guild with a progression row but NO bankLimit rewards on any level
        // (0 = "not granted" in the config model). Previously the loop treated 0
        // as a real limit, so effectiveMaxBalance(cap, 0) = 0 blocked every
        // deposit. Must be null → config cap applies.
        val rewards = mapOf(
            1 to levelReward(bankLimit = 0),
            2 to levelReward(bankLimit = 0),
            3 to levelReward(bankLimit = 0)
        )

        val limit = BankServiceBukkit.computeProgressionBankLimit(rewards, currentLevel = 3)

        assertEquals(null, limit)
        assertEquals(1_000_000, BankServiceBukkit.effectiveMaxBalance(1_000_000, limit))
    }

    @Test
    fun `progression loop takes the highest bankLimit across reached levels`() {
        val rewards = mapOf(
            1 to levelReward(bankLimit = 50_000),
            2 to levelReward(bankLimit = 200_000),
            3 to levelReward(bankLimit = 100_000)
        )

        assertEquals(200_000, BankServiceBukkit.computeProgressionBankLimit(rewards, currentLevel = 3))
    }

    @Test
    fun `progression loop ignores levels beyond the current level`() {
        val rewards = mapOf(
            1 to levelReward(bankLimit = 50_000),
            2 to levelReward(bankLimit = 200_000),
            3 to levelReward(bankLimit = 100_000)
        )

        assertEquals(50_000, BankServiceBukkit.computeProgressionBankLimit(rewards, currentLevel = 1))
    }

    @Test
    fun `progression loop mixes granted and ungranted levels`() {
        val rewards = mapOf(
            1 to levelReward(bankLimit = 0),
            2 to levelReward(bankLimit = 75_000),
            3 to levelReward(bankLimit = 0)
        )

        assertEquals(75_000, BankServiceBukkit.computeProgressionBankLimit(rewards, currentLevel = 3))
    }

    private fun levelReward(bankLimit: Int): net.lumalyte.lg.config.LevelRewardConfig {
        return net.lumalyte.lg.config.LevelRewardConfig(bankLimit = bankLimit)
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
