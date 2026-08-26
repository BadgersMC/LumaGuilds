package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.domain.entities.BankSettings
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockBankSettingsEditorTest {
    private val guildId = UUID.randomUUID()

    @Test
    fun `budget save persists nonnegative limits and preserves automation settings`() {
        val repository = RecordingRepository(
            BankSettings(guildId, scheduledDepositsEnabled = true, interestRate = 0.03)
        )
        val editor = BedrockBankSettingsEditor(repository)

        val result = editor.saveBudgets(guildId, "12000", "3000", "750")

        assertTrue(result)
        assertEquals(12000, repository.saved?.monthlyBudget)
        assertEquals(3000, repository.saved?.weeklyBudget)
        assertEquals(750, repository.saved?.dailyBudget)
        assertTrue(repository.saved?.scheduledDepositsEnabled == true)
        assertEquals(0.03, repository.saved?.interestRate)
    }

    @Test
    fun `budget save rejects negative or nonnumeric values without writing`() {
        val repository = RecordingRepository(BankSettings(guildId))
        val editor = BedrockBankSettingsEditor(repository)

        assertFalse(editor.saveBudgets(guildId, "100", "-1", "25"))
        assertFalse(editor.saveBudgets(guildId, "nope", "50", "25"))
        assertEquals(null, repository.saved)
    }

    @Test
    fun `automation save converts percent to fraction and persists toggles`() {
        val repository = RecordingRepository(BankSettings(guildId, monthlyBudget = 42000))
        val editor = BedrockBankSettingsEditor(repository)

        val result = editor.saveAutomation(guildId, true, false, true, "2.5")

        assertTrue(result)
        assertTrue(repository.saved?.scheduledDepositsEnabled == true)
        assertFalse(repository.saved?.autoRewardsEnabled == true)
        assertTrue(repository.saved?.recurringPaymentsEnabled == true)
        assertEquals(0.025, repository.saved?.interestRate)
        assertEquals(42000, repository.saved?.monthlyBudget)
    }

    @Test
    fun `security save persists a nonnegative dual auth threshold`() {
        val repository = RecordingRepository(BankSettings(guildId))
        val editor = BedrockBankSettingsEditor(repository)

        assertTrue(editor.saveSecurity(guildId, "2500"))
        assertEquals(2500, repository.saved?.dualAuthThreshold)
        repository.saved = null
        assertFalse(editor.saveSecurity(guildId, "-1"))
        assertEquals(null, repository.saved)
    }

    @Test
    fun `auto deposit save persists both enabled and disabled states`() {
        val repository = RecordingRepository(BankSettings(guildId, scheduledDepositsEnabled = true))
        val editor = BedrockBankSettingsEditor(repository)

        assertTrue(editor.saveAutoDeposit(guildId, false))
        assertFalse(repository.saved?.scheduledDepositsEnabled == true)
    }

    private class RecordingRepository(private val current: BankSettings?) : BankSettingsRepository {
        var saved: BankSettings? = null

        override fun getByGuildId(guildId: UUID): BankSettings? = current

        override fun upsert(settings: BankSettings): Boolean {
            saved = settings
            return true
        }
    }
}
