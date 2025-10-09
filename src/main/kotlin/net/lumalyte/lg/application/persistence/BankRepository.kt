package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankTransaction
import java.util.UUID

/**
 * Repository interface for bank transaction persistence.
 */
interface BankRepository {

    /**
     * Records a bank transaction.
     *
     * @param transaction The transaction to record.
     * @return true if successful, false otherwise.
     */
    fun recordTransaction(transaction: BankTransaction): Boolean

    /**
     * Gets all transactions for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of transactions for the guild.
     */
    fun getTransactionsForGuild(guildId: UUID, limit: Int? = null): List<BankTransaction>

    /**
     * Gets all transactions for a specific player across all guilds.
     *
     * @param playerId The ID of the player.
     * @return List of transactions for the player.
     */
    fun getTransactionsByPlayerId(playerId: UUID): List<BankTransaction>

    /**
     * Gets the current balance for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The current balance, or 0 if no transactions exist.
     */
    fun getGuildBalance(guildId: UUID): Int

    /**
     * Gets the total amount deposited by a player to a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The total amount deposited.
     */
    fun getPlayerTotalDeposits(playerId: UUID, guildId: UUID): Int

    /**
     * Gets the total amount withdrawn by a player from a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return The total amount withdrawn.
     */
    fun getPlayerTotalWithdrawals(playerId: UUID, guildId: UUID): Int

    /**
     * Records an audit entry.
     *
     * @param audit The audit entry to record.
     * @return true if successful, false otherwise.
     */
    fun recordAudit(audit: BankAudit): Boolean

    /**
     * Gets audit entries for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit Optional limit for the number of results.
     * @return List of audit entries for the guild.
     */
    fun getAuditForGuild(guildId: UUID, limit: Int? = null): List<BankAudit>

    /**
     * Gets audit entries for a player.
     *
     * @param playerId The ID of the player.
     * @param limit Optional limit for the number of results.
     * @return List of audit entries for the player.
     */
    fun getAuditForPlayer(playerId: UUID, limit: Int? = null): List<BankAudit>

    /**
     * Gets the total number of transactions for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The total number of transactions.
     */
    fun getTransactionCountForGuild(guildId: UUID): Int

    /**
     * Gets the total transaction volume for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The total transaction volume.
     */
    fun getTotalVolumeForGuild(guildId: UUID): Int

    /**
     * Clears all transactions for a guild (used for testing or admin operations).
     *
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun clearGuildTransactions(guildId: UUID): Boolean
}
