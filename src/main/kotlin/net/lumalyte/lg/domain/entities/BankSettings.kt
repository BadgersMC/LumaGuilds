package net.lumalyte.lg.domain.entities

import java.util.UUID

/**
 * Per-guild bank automation, budget, and security settings (REQ-010/011/031).
 *
 * Persisted via [net.lumalyte.lg.application.persistence.BankSettingsRepository];
 * values are the menu-editable knobs, distinct from the global [net.lumalyte.lg.config.BankConfig].
 */
data class BankSettings(
    val guildId: UUID,
    var scheduledDepositsEnabled: Boolean = false,
    var autoRewardsEnabled: Boolean = true,
    var recurringPaymentsEnabled: Boolean = false,
    var interestRate: Double = 0.02, // fraction (0.02 = 2%) per compound period
    var dualAuthThreshold: Int = 1000,
    var monthlyBudget: Int = 10000,
    var weeklyBudget: Int = 2500,
    var dailyBudget: Int = 500,
    var lastInterestAccrual: Long = 0L // epoch millis; 0 = never accrued
)
