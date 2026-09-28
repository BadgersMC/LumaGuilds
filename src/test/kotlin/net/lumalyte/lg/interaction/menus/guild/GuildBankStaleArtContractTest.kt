package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class GuildBankStaleArtContractTest {
    private val bank = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankMenu.kt"
    ).toFile().readText()
    private val automation = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankAutomationMenu.kt"
    ).toFile().readText()

    @Test
    fun existingBankControlsUseExactStaleMenuArtConcepts() {
        listOf(
            "lg_history",
            "lg_automation",
            "lg_bank_statistics",
            "lg_bank_member_contributions",
        ).forEach { assertTrue(bank.contains(it), "Missing bank art hook $it") }

        listOf(
            "lg_bank_scheduled_deposits",
            "lg_bank_auto_rewards",
            "lg_bank_budget_alerts",
            "lg_bank_recurring_payments",
            "lg_bank_interest",
            "lg_bank_active_automations",
            "lg_bank_inactive_automations",
        ).forEach { assertTrue(automation.contains(it), "Missing automation art hook $it") }
    }
}
