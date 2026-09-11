package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.interaction.menus.guild.GuildBankWithdrawal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildBankWithdrawalTest {
    @Test
    fun `withdraw all uses deposits made after the action was created`() {
        var balance = 0L
        val withdrawal = GuildBankWithdrawal { balance }
        balance = 1_000L
        assertEquals(1_000, withdrawal.resolveAmount(-1))
        balance = 250L
        assertEquals(250, withdrawal.resolveAmount(-1))
    }

    @Test
    fun `fixed withdrawal amounts do not become withdraw all`() {
        val withdrawal = GuildBankWithdrawal { error("Fixed amount must not read balance") }
        listOf(100, 1_000, 10_000).forEach { assertEquals(it, withdrawal.resolveAmount(it)) }
    }

    @Test
    fun `empty vault and negative amounts never execute a transfer`() {
        val withdrawal = GuildBankWithdrawal { 0L }
        var rejections = 0
        listOf(withdrawal.resolveAmount(-1), -5).forEach { amount ->
            assertFalse(withdrawal.execute(amount,
                onEmpty = { rejections++ },
                transfer = { error("Rejected amount must not debit gold or deliver items") }))
        }
        assertEquals(2, rejections)
    }

    @Test
    fun `positive amount reaches transfer exactly once and preserves its result`() {
        val withdrawal = GuildBankWithdrawal { 1_000L }
        val transfers = mutableListOf<Int>()
        assertTrue(withdrawal.execute(100, { error("Unexpected rejection") }) {
            transfers += it
            true
        })
        assertEquals(listOf(100), transfers)
        assertFalse(withdrawal.execute(100, { error("Unexpected rejection") }) { false })
    }
}
