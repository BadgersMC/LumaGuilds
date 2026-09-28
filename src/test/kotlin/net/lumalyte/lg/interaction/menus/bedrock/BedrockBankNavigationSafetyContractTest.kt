package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class BedrockBankNavigationSafetyContractTest {
    private val root = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock")
    private val bank = File(root, "BedrockGuildBankMenu.kt").readText()
    private val editor = File(root, "BedrockBankSettingsEditor.kt").readText()

    @Test
    fun `main bank routes to every supported Bedrock bank surface`() {
        listOf(
            "createGuildBankTransactionHistoryMenu",
            "createGuildBankStatisticsMenu",
            "createGuildMemberContributionsMenu",
            "createGuildBankAutomationMenu",
            "createGuildBankBudgetMenu",
            "createGuildBankSecurityMenu"
        ).forEach { route ->
            assertTrue(bank.contains(route), "missing bank navigation route: $route")
        }
    }

    @Test
    fun `management navigation and mutations share the bank settings authority`() {
        assertTrue(bank.contains("authorization.canManageBankSettings"))
        assertTrue(bank.contains("bedrock.bank.navigation.management"))
        assertTrue(editor.contains("if (!canManageSettings(guildId)) return false"))
    }

    @Test
    fun `canonical transaction and ambiguous withdrawal behavior stay intact`() {
        assertTrue(bank.contains("bankService.deposit("))
        assertTrue(bank.contains("bankService.withdrawOutcome("))
        assertTrue(bank.contains("BankWithdrawalResult.Ambiguous"))
        assertTrue(bank.contains("menu.bank.feedback.withdraw_pending"))
    }
}
