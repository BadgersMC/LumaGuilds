package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockBankDrilldownParityContractTest {
    private val root = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock")
    private val history = File(root, "BedrockGuildBankTransactionHistoryMenu.kt").readText()
    private val contributions = File(root, "BedrockGuildMemberContributionsMenu.kt").readText()
    private val automation = File(root, "BedrockGuildBankAutomationMenu.kt").readText()
    private val editor = File(root, "BedrockBankSettingsEditor.kt").readText()

    @Test
    fun `history is bounded paged and filters the authoritative transaction model`() {
        assertTrue(history.contains("private const val HISTORY_LOAD_LIMIT"))
        assertTrue(history.contains("private const val PAGE_SIZE"))
        assertTrue(history.contains("TransactionType.entries"))
        assertTrue(history.contains("memberService.getGuildMembers(guild.id)"))
        assertTrue(history.contains("searchQuery"))
        assertTrue(history.contains("dateRange"))
        assertTrue(history.contains("transaction.type"))
        assertFalse(history.contains("getTransactionHistory(guild.id, 15)"))
        assertFalse(history.contains("transaction.amount > 0"))
    }
    @Test
    fun `contributions page the complete bounded service result instead of top ten only`() {
        assertTrue(contributions.contains("private const val PAGE_SIZE"))
        assertTrue(contributions.contains("pageCount"))
        assertTrue(contributions.contains(".drop(page * PAGE_SIZE)"))
        assertTrue(contributions.contains(".take(PAGE_SIZE)"))
        assertFalse(contributions.contains(".take(10)"))
    }

    @Test
    fun `automation distinguishes retained flags from the one executing automation`() {
        assertTrue(automation.contains("BankAutomationService"))
        assertTrue(automation.contains("getNextInterestRun(guild.id)"))
        assertTrue(automation.contains("bedrock.bank_automation.read_only"))
        assertTrue(automation.contains("saveInterestRate"))
        assertFalse(automation.contains("response.asToggle(0)"))
        assertFalse(automation.contains("response.asToggle(1)"))
        assertFalse(automation.contains("response.asToggle(2)"))
        assertTrue(editor.contains("fun saveInterestRate"))
        assertTrue(editor.contains("if (!canManageSettings(guildId)) return false"))
    }
}
