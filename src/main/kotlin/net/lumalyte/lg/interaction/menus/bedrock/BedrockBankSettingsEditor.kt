package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.domain.entities.BankSettings
import java.util.UUID

/** Applies validated Bedrock form values without discarding unrelated bank settings. */
class BedrockBankSettingsEditor(
    private val repository: BankSettingsRepository
) {
    fun saveBudgets(guildId: UUID, monthly: String, weekly: String, daily: String): Boolean {
        val monthlyValue = monthly.toNonnegativeInt() ?: return false
        val weeklyValue = weekly.toNonnegativeInt() ?: return false
        val dailyValue = daily.toNonnegativeInt() ?: return false
        val current = repository.getByGuildId(guildId) ?: BankSettings(guildId)
        return repository.upsert(
            current.copy(
                monthlyBudget = monthlyValue,
                weeklyBudget = weeklyValue,
                dailyBudget = dailyValue
            )
        )
    }

    fun saveAutomation(
        guildId: UUID,
        scheduledDeposits: Boolean,
        autoRewards: Boolean,
        recurringPayments: Boolean,
        interestPercent: String
    ): Boolean {
        val percent = interestPercent.trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
            ?: return false
        val current = repository.getByGuildId(guildId) ?: BankSettings(guildId)
        return repository.upsert(
            current.copy(
                scheduledDepositsEnabled = scheduledDeposits,
                autoRewardsEnabled = autoRewards,
                recurringPaymentsEnabled = recurringPayments,
                interestRate = percent / 100.0
            )
        )
    }

    fun saveSecurity(guildId: UUID, dualAuthThreshold: String): Boolean {
        val threshold = dualAuthThreshold.toNonnegativeInt() ?: return false
        val current = repository.getByGuildId(guildId) ?: BankSettings(guildId)
        return repository.upsert(current.copy(dualAuthThreshold = threshold))
    }

    fun saveAutoDeposit(guildId: UUID, enabled: Boolean): Boolean {
        val current = repository.getByGuildId(guildId) ?: BankSettings(guildId)
        return repository.upsert(current.copy(scheduledDepositsEnabled = enabled))
    }

    private fun String.toNonnegativeInt(): Int? = trim().toIntOrNull()?.takeIf { it >= 0 }
}
