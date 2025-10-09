package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.BudgetCategory
import net.lumalyte.lg.domain.entities.GuildBudget
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for guild budget management.
 */
interface GuildBudgetRepository {
    /**
     * Finds all budgets for a guild.
     */
    fun findByGuildId(guildId: UUID): List<GuildBudget>

    /**
     * Finds a budget by guild ID and category.
     */
    fun findByGuildIdAndCategory(guildId: UUID, category: BudgetCategory): GuildBudget?

    /**
     * Saves or updates a budget.
     */
    fun save(budget: GuildBudget): Boolean

    /**
     * Updates the spent amount for a budget.
     */
    fun updateSpentAmount(guildId: UUID, category: BudgetCategory, spentAmount: Int): Boolean

    /**
     * Deletes a budget by guild ID and category.
     */
    fun deleteByGuildIdAndCategory(guildId: UUID, category: BudgetCategory): Boolean

    /**
     * Finds budgets expiring within a time period.
     */
    fun findExpiringBudgets(beforeDate: Instant): List<GuildBudget>

    /**
     * Finds over-budget items.
     */
    fun findOverBudgetItems(): List<GuildBudget>

    /**
     * Gets budget analytics for a guild within a time period.
     */
    fun getBudgetAnalytics(guildId: UUID, startDate: Instant, endDate: Instant): Map<BudgetCategory, BudgetAnalyticsData>
}

/**
 * Data class for budget analytics calculations.
 */
data class BudgetAnalyticsData(
    val category: BudgetCategory,
    val allocatedAmount: Int,
    val spentAmount: Int,
    val transactionCount: Int,
    val averageTransactionAmount: Double,
    val alertsTriggered: Int
)
