package net.lumalyte.lg.interaction.menus

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Source contracts for the GUI wiring; these do not replace in-game inventory testing. */
class GuildBankWithdrawalContractTest {
    private val source = Files.readString(Path.of(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankMenu.kt"
    ))

    @Test
    fun `withdraw all resolves live guild balance instead of menu snapshot`() {
        val action = source.substringAfter("private fun handleQuickAction(")
            .substringBefore("// Show loading state")
        val all = action.substringAfter("val actualAmount:")
        assertTrue(all.contains("vaultInventoryManager.getGoldBalance(guild.id).toInt()"),
            "Withdraw All must read the live balance when clicked")
        assertFalse(all.contains("currentBalance.toInt()"),
            "A deposit after opening the menu must not leave Withdraw All using the old snapshot")
    }

    @Test
    fun `nonpositive withdrawals return before balance debit and item delivery`() {
        val withdrawal = source.substringAfter("private fun handleWithdrawal(amount: Int): Boolean {")
        val guard = Regex("if\\s*\\(amount\\s*<=\\s*0\\)\\s*\\{([^}]+)}").find(withdrawal)
        assertTrue(guard != null, "Reject both zero and negative withdrawal amounts")
        assertTrue(guard.value.contains("return false"))
        assertTrue(guard.value.contains("menu.bank.feedback.withdraw_no_amount"))
        val debit = withdrawal.indexOf("vaultInventoryManager.withdrawGold(")
        val delivery = withdrawal.indexOf("GoldBalanceButton.convertToItems(")
        assertTrue(debit > guard.range.last, "Reject before any guild gold debit")
        assertTrue(delivery > guard.range.last, "Reject before materializing inventory items")
    }
}
