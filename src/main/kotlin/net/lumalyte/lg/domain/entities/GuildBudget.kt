package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Guild budget entity for tracking spending limits and alerts.
 */
data class GuildBudget(
    val id: UUID,
    val guildId: UUID,
    val category: BudgetCategory,
    val allocatedAmount: Int,
    val spentAmount: Int = 0,
    val periodStart: Instant,
    val periodEnd: Instant,
    val alertsEnabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    fun getRemainingAmount(): Int {
        return allocatedAmount - spentAmount
    }

    fun getUsagePercentage(): Double {
        return if (allocatedAmount > 0) (spentAmount.toDouble() / allocatedAmount) * 100 else 0.0
    }

    fun isOverBudget(): Boolean {
        return spentAmount > allocatedAmount
    }

    fun isNearLimit(threshold: Double = 80.0): Boolean {
        return getUsagePercentage() >= threshold
    }

    fun isExpired(): Boolean {
        return Instant.now().isAfter(periodEnd)
    }

    fun getStatus(): BudgetStatus {
        return when {
            isExpired() -> BudgetStatus.EXPIRED
            isOverBudget() -> BudgetStatus.OVER_BUDGET
            isNearLimit() -> BudgetStatus.NEAR_LIMIT
            else -> BudgetStatus.ACTIVE
        }
    }
}

/**
 * Budget categories for different types of guild spending.
 */
enum class BudgetCategory {
    WAR_CHESTS,
    PARTY_EVENTS,
    GUILD_MAINTENANCE,
    MEMBER_REWARDS,
    EMERGENCY_FUNDS,
    GENERAL_EXPENSES
}

/**
 * Budget status indicating current state and alerts.
 */
enum class BudgetStatus {
    ACTIVE,
    NEAR_LIMIT,
    OVER_BUDGET,
    EXPIRED
}

/**
 * Budget alert configuration for automated notifications.
 */
data class BudgetAlert(
    val id: UUID,
    val guildId: UUID,
    val budgetId: UUID,
    val alertType: BudgetAlertType,
    val threshold: Double,
    val message: String,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now()
)

/**
 * Types of budget alerts that can be configured.
 */
enum class BudgetAlertType {
    USAGE_PERCENTAGE,
    REMAINING_AMOUNT,
    OVER_BUDGET,
    EXPIRING_SOON
}
