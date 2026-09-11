package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.interaction.menus.guild.GuildBankWithdrawal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for live withdrawal amounts and validation before transfer. */
internal class GuildBankWithdrawalTest {
    /** Withdraw All observes deposits and withdrawals made after the menu action is created. */
    @Test
    fun readsLiveBalance() {
        var balance = 0L
        val withdrawal = GuildBankWithdrawal { balance }
        balance = DEPOSITED_BALANCE.toLong()
        assertEquals(DEPOSITED_BALANCE, withdrawal.resolveAmount(-1))
        balance = REMAINING_BALANCE.toLong()
        assertEquals(REMAINING_BALANCE, withdrawal.resolveAmount(-1))
    }

    /** Fixed amounts bypass the balance lookup and retain the selected quantity. */
    @Test
    fun preservesFixedAmounts() {
        val withdrawal = GuildBankWithdrawal { error("Fixed amount must not read balance") }
        listOf(SMALL_WITHDRAWAL, DEPOSITED_BALANCE, LARGE_WITHDRAWAL).forEach {
            assertEquals(it, withdrawal.resolveAmount(it))
        }
    }

    /** Empty and negative requests reject before invoking the transfer callback. */
    @Test
    fun rejectsNonpositiveAmounts() {
        val withdrawal = GuildBankWithdrawal { 0L }
        var rejections = 0
        listOf(withdrawal.resolveAmount(-1), NEGATIVE_WITHDRAWAL).forEach { amount ->
            assertFalse(
                withdrawal.execute(
                    amount,
                    onEmpty = { rejections++ },
                    transfer = { error("Rejected amount must not debit gold or deliver items") },
                ),
            )
        }
        assertEquals(2, rejections)
    }

    /** Valid requests invoke one transfer and propagate success or failure. */
    @Test
    fun propagatesTransferResult() {
        val withdrawal = GuildBankWithdrawal { DEPOSITED_BALANCE.toLong() }
        val transfers = mutableListOf<Int>()
        assertTrue(
            withdrawal.execute(SMALL_WITHDRAWAL, { error("Unexpected rejection") }) {
                transfers += it
                true
            },
        )
        assertEquals(listOf(SMALL_WITHDRAWAL), transfers)
        assertFalse(withdrawal.execute(SMALL_WITHDRAWAL, { error("Unexpected rejection") }) { false })
    }

    private companion object {
        const val DEPOSITED_BALANCE = 1_000
        const val REMAINING_BALANCE = 250
        const val SMALL_WITHDRAWAL = 100
        const val LARGE_WITHDRAWAL = 10_000
        const val NEGATIVE_WITHDRAWAL = -5
    }
}
