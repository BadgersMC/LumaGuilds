package net.lumalyte.lg.domain.entities

import java.time.Instant

/**
 * Analytics data for guild budget performance over a specific period.
 */
data class BudgetAnalytics(
    val category: BudgetCategory,
    val allocatedAmount: Int,
    val spentAmount: Int,
    val remainingAmount: Int,
    val usagePercentage: Double,
    val status: BudgetStatus,
    val periodStart: Instant,
    val periodEnd: Instant,
    val transactionCount: Int,
    val averageTransactionAmount: Double,
    val alertsTriggered: List<String>
)
