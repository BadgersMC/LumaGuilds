package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankTransaction
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
