package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankSecuritySettings
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.BudgetAnalytics
import net.lumalyte.lg.domain.entities.BudgetCategory
import net.lumalyte.lg.domain.entities.GuildBudget
import net.lumalyte.lg.domain.entities.MemberContribution
import java.util.UUID

/**
 * Service interface for guild bank operations.
 */
interface BankService {

    /**
     * Deposits money into a guild's bank.
     *
     * @param guildId The ID of the guild.
     * @param playerId The ID of the player making the deposit.
     * @param amount The amount to deposit.
     * @param description Optional description for the transaction.
     * @return The transaction if successful, null otherwise.
     */
    fun deposit(guildId: UUID, playerId: UUID, amount: Int, description: String? = null): BankTransaction?

    /**
     * Withdraws money from a guild's bank.
     *
     * @param guildId The ID of the guild.
     * @param playerId The ID of the player making the withdrawal.
     * @param amount The amount to withdraw.
     * @param description Optional description for the transaction.
     * @return The transaction if successful, null otherwise.
     */
    fun withdraw(guildId: UUID, playerId: UUID, amount: Int, description: String? = null): BankTransaction?

    /**
     * Gets the current balance of a guild's bank.
     *
     * @param guildId The ID of the guild.
     * @return The current balance.
     */
    fun getBalance(guildId: UUID): Int

    /**
     * Gets the current balance of a player's economy account.
     *
     * @param playerId The ID of the player.
     * @return The current player balance.
     */
    fun getPlayerBalance(playerId: UUID): Int

    /**
     * Withdraws money from a player's economy account.
     *
     * @param playerId The ID of the player.
     * @param amount The amount to withdraw.
     * @param reason Optional reason for the transaction.
     * @return true if successful, false otherwise.
     */
    fun withdrawPlayer(playerId: UUID, amount: Int, reason: String? = null): Boolean

    /**
     * Deposits money into a player's economy account.
     *
     * @param playerId The ID of the player.
     * @param amount The amount to deposit.
     * @param reason Optional reason for the transaction.
     * @return true if successful, false otherwise.
     */
    fun depositPlayer(playerId: UUID, amount: Int, reason: String? = null): Boolean

    /**
     * Deducts money from a guild's bank without giving it to any player.
     *
     * @param guildId The ID of the guild.
     * @param amount The amount to deduct.
     * @param reason Optional reason for the transaction.
     * @return true if successful, false otherwise.
     */
    fun deductFromGuildBank(guildId: UUID, amount: Int, reason: String? = null): Boolean

    /**
     * Checks if a player can withdraw from a guild's bank.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can withdraw, false otherwise.
     */
    fun canWithdraw(playerId: UUID, guildId: UUID): Boolean

    /**
     * Checks if a player can deposit into a guild's bank.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can deposit, false otherwise.
     */
    fun canDeposit(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets the transaction history for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of transactions.
     */
    fun getTransactionHistory(guildId: UUID, limit: Int? = null): List<BankTransaction>

    /**
     * Gets the audit log for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of audit entries.
     */
    fun getAuditLog(guildId: UUID, limit: Int? = null): List<BankAudit>

    /**
     * Gets transactions by player ID.
     *
     * @param playerId The ID of the player.
     * @return List of transactions for the player.
     */
    fun getTransactionsByPlayerId(playerId: UUID): List<BankTransaction>

    /**
     * Records a bank audit entry.
     *
     * @param audit The audit entry to record.
     * @return true if successful, false otherwise.
     */
    fun recordAudit(audit: BankAudit): Boolean

    /**
     * Gets the balance at a specific time.
     *
     * @param guildId The ID of the guild.
     * @param timestamp The timestamp to check balance at.
     * @return The balance at that time, or null if not found.
     */
    fun getBalanceAtTime(guildId: UUID, timestamp: java.time.Instant): Int?

    /**
     * Gets the member contributions summary for a guild.
     * Shows each member's net contribution (deposits - withdrawals).
     *
     * @param guildId The ID of the guild.
     * @return List of member contributions sorted by net contribution (descending).
     */
    fun getMemberContributions(guildId: UUID): List<MemberContribution>

    /**
     * Gets the total deposits made by a player to a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The total amount deposited.
     */
    fun getPlayerDeposits(playerId: UUID, guildId: UUID): Int

    /**
     * Gets the total withdrawals made by a player from a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The total amount withdrawn.
     */
    fun getPlayerWithdrawals(playerId: UUID, guildId: UUID): Int

    /**
     * Calculates the fee for a withdrawal.
     *
     * @param guildId The ID of the guild.
     * @param amount The withdrawal amount.
     * @return The fee amount.
     */
    fun calculateWithdrawalFee(guildId: UUID, amount: Int): Int

    /**
     * Gets the maximum withdrawal amount for a guild.
     *
     * @param guildId The ID of the guild.
     * @param playerId The ID of the player (for permission checks).
     * @return The maximum withdrawal amount.
     */
    fun getMaxWithdrawalAmount(guildId: UUID, playerId: UUID): Int

    /**
     * Gets the minimum deposit amount allowed.
     *
     * @return The minimum deposit amount.
     */
    fun getMinDepositAmount(): Int

    /**
     * Gets the maximum deposit amount allowed per transaction.
     *
     * @return The maximum deposit amount.
     */
    fun getMaxDepositAmount(): Int

    /**
     * Checks if a guild's bank has sufficient funds for a withdrawal.
     *
     * @param guildId The ID of the guild.
     * @param amount The amount to check.
     * @param includeFee Whether to include withdrawal fees in the check.
     * @return true if sufficient funds, false otherwise.
     */
    fun hasSufficientFunds(guildId: UUID, amount: Int, includeFee: Boolean = true): Boolean

    /**
     * Gets the bank statistics for a guild.
     *
     * @param guildId The ID of the guild.
     * @return Bank statistics.
     */
    fun getBankStats(guildId: UUID): BankStats

    /**
     * Processes expired bank items (cleanup task).
     *
     * @return The number of items processed.
     */
    fun processExpiredItems(): Int

    /**
     * Validates a transaction amount.
     *
     * @param amount The amount to validate.
     * @return true if valid, false otherwise.
     */
    fun isValidAmount(amount: Int): Boolean

    // === SECURITY ENHANCEMENTS ===

    /**
     * Gets the security settings for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The security settings, or null if not found.
     */
    fun getSecuritySettings(guildId: UUID): BankSecuritySettings?

    /**
     * Updates the security settings for a guild.
     *
     * @param guildId The ID of the guild.
     * @param settings The updated security settings.
     * @return true if successful, false otherwise.
     */
    fun updateSecuritySettings(guildId: UUID, settings: BankSecuritySettings): Boolean

    /**
     * Checks if a transaction requires dual authorization.
     *
     * @param guildId The ID of the guild.
     * @param amount The transaction amount.
     * @return true if dual authorization is required.
     */
    fun requiresDualAuth(guildId: UUID, amount: Int): Boolean

    /**
     * Checks if a transaction requires multi-signature approval.
     *
     * @param guildId The ID of the guild.
     * @param amount The transaction amount.
     * @return true if multi-signature is required.
     */
    fun requiresMultiSignature(guildId: UUID, amount: Int): Boolean

    /**
     * Logs a security audit event.
     *
     * @param guildId The ID of the guild.
     * @param playerId The ID of the player.
     * @param action The audit action.
     * @param amount The transaction amount (optional).
     * @param description Additional description (optional).
     * @return The created audit entry.
     */
    fun logSecurityEvent(guildId: UUID, playerId: UUID, action: net.lumalyte.lg.domain.entities.AuditAction, amount: Int? = null, description: String? = null): BankAudit?

    /**
     * Checks for suspicious activity in a guild's bank.
     *
     * @param guildId The ID of the guild.
     * @return List of suspicious activities detected.
     */
    fun detectSuspiciousActivity(guildId: UUID): List<String>

    /**
     * Activates emergency freeze for a guild's bank.
     *
     * @param guildId The ID of the guild.
     * @param activatedBy The ID of the player activating the freeze.
     * @param reason The reason for the freeze.
     * @return true if successful, false otherwise.
     */
    fun activateEmergencyFreeze(guildId: UUID, activatedBy: UUID, reason: String): Boolean

    /**
     * Deactivates emergency freeze for a guild's bank.
     *
     * @param guildId The ID of the guild.
     * @param deactivatedBy The ID of the player deactivating the freeze.
     * @return true if successful, false otherwise.
     */
    fun deactivateEmergencyFreeze(guildId: UUID, deactivatedBy: UUID): Boolean

    // === BUDGET MANAGEMENT ===

    /**
     * Gets all budgets for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of guild budgets.
     */
    fun getGuildBudgets(guildId: UUID): List<GuildBudget>

    /**
     * Gets a specific budget by category.
     *
     * @param guildId The ID of the guild.
     * @param category The budget category.
     * @return The budget, or null if not found.
     */
    fun getBudgetByCategory(guildId: UUID, category: BudgetCategory): GuildBudget?

    /**
     * Creates or updates a budget for a guild.
     *
     * @param guildId The ID of the guild.
     * @param category The budget category.
     * @param allocatedAmount The allocated amount.
     * @param periodStart The start of the budget period.
     * @param periodEnd The end of the budget period.
     * @return The created/updated budget, or null if failed.
     */
    fun setBudget(guildId: UUID, category: BudgetCategory, allocatedAmount: Int, periodStart: java.time.Instant, periodEnd: java.time.Instant): GuildBudget?

    /**
     * Updates the spent amount for a budget category.
     *
     * @param guildId The ID of the guild.
     * @param category The budget category.
     * @param amount The amount spent.
     * @return true if successful, false otherwise.
     */
    fun updateBudgetSpent(guildId: UUID, category: BudgetCategory, amount: Int): Boolean

    /**
     * Gets budget analytics for a guild.
     *
     * @param guildId The ID of the guild.
     * @param periodDays Number of days to analyze.
     * @return Budget analytics data.
     */
    fun getBudgetAnalytics(guildId: UUID, periodDays: Int = 30): Map<BudgetCategory, BudgetAnalytics>

    /**
     * Checks if a transaction would exceed budget limits.
     *
     * @param guildId The ID of the guild.
     * @param category The budget category.
     * @param amount The transaction amount.
     * @return true if within budget, false if would exceed.
     */
    fun isWithinBudget(guildId: UUID, category: BudgetCategory, amount: Int): Boolean
}

/**
 * Data class for bank statistics.
 */
data class BankStats(
    val currentBalance: Int,
    val totalDeposits: Int,
    val totalWithdrawals: Int,
    val totalTransactions: Int,
    val transactionVolume: Int
)

/**
 * Data class for budget analytics.
 */
data class BudgetAnalytics(
    val category: BudgetCategory,
    val allocatedAmount: Int,
    val spentAmount: Int,
    val remainingAmount: Int,
    val usagePercentage: Double,
    val status: net.lumalyte.lg.domain.entities.BudgetStatus,
    val periodStart: java.time.Instant,
    val periodEnd: java.time.Instant,
    val transactionCount: Int,
    val averageTransactionAmount: Double,
    val alertsTriggered: Int
)
