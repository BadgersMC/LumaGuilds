package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.*
import java.util.UUID

/**
 * Hytale implementation of BankService.
 *
 * Handles guild bank operations including deposits, withdrawals, and transaction tracking.
 * This implementation uses a virtual economy system (no physical vault integration yet).
 */
class HytaleBankService(
    private val bankRepository: BankRepository,
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val rankService: RankService
) : BankService {

    companion object {
        const val MIN_DEPOSIT = 1
        const val MAX_DEPOSIT = 1000000
        const val WITHDRAWAL_FEE_PERCENT = 0 // No fees for now
    }

    override fun deposit(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankTransaction? {
        // Validate amount
        if (!isValidAmount(amount)) {
            return null
        }

        // Check if player can deposit
        if (!canDeposit(playerId, guildId)) {
            recordAuditDenied(guildId, playerId, "DEPOSIT", "No permission to deposit")
            return null
        }

        // Check player balance (placeholder - would integrate with economy plugin)
        val playerBalance = getPlayerBalance(playerId)
        if (playerBalance < amount) {
            recordAuditDenied(guildId, playerId, "DEPOSIT", "Insufficient player funds")
            return null
        }

        // Withdraw from player
        if (!withdrawPlayer(playerId, amount, "Guild bank deposit")) {
            return null
        }

        // Create and record transaction
        val transaction = BankTransaction.deposit(guildId, playerId, amount, description)
        if (!bankRepository.recordTransaction(transaction)) {
            // Rollback player withdrawal
            depositPlayer(playerId, amount, "Failed guild bank deposit rollback")
            return null
        }

        // Record audit
        val oldBalance = getBalance(guildId) - amount
        recordAuditSuccess(guildId, playerId, "DEPOSIT", amount, oldBalance, getBalance(guildId))

        return transaction
    }

    override fun withdraw(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankTransaction? {
        // Validate amount
        if (!isValidAmount(amount)) {
            return null
        }

        // Check if player can withdraw
        if (!canWithdraw(playerId, guildId)) {
            recordAuditDenied(guildId, playerId, "WITHDRAWAL", "No permission to withdraw")
            return null
        }

        // Calculate fee
        val fee = calculateWithdrawalFee(guildId, amount)
        val total = amount + fee

        // Check guild balance
        if (!hasSufficientFunds(guildId, amount, includeFee = true)) {
            recordAuditDenied(guildId, playerId, "WITHDRAWAL", "Insufficient guild funds")
            return null
        }

        // Create and record transaction
        val transaction = BankTransaction.withdraw(guildId, playerId, amount, fee, description)
        if (!bankRepository.recordTransaction(transaction)) {
            return null
        }

        // Deposit to player
        if (!depositPlayer(playerId, amount, "Guild bank withdrawal")) {
            // Rollback transaction
            bankRepository.deleteTransaction(transaction.id)
            return null
        }

        // Record audit
        val oldBalance = getBalance(guildId) + total
        recordAuditSuccess(guildId, playerId, "WITHDRAWAL", amount, oldBalance, getBalance(guildId))

        return transaction
    }

    override fun getBalance(guildId: UUID): Int {
        return bankRepository.getGuildBalance(guildId)
    }

    override fun getPlayerBalance(playerId: UUID): Int {
        // Placeholder - would integrate with Hytale economy plugin
        // For now, return a large number for testing
        return 1000000
    }

    override fun withdrawPlayer(playerId: UUID, amount: Int, reason: String?): Boolean {
        // Placeholder - would integrate with Hytale economy plugin
        return true
    }

    override fun depositPlayer(playerId: UUID, amount: Int, reason: String?): Boolean {
        // Placeholder - would integrate with Hytale economy plugin
        return true
    }

    override fun deductFromGuildBank(guildId: UUID, amount: Int, reason: String?): Boolean {
        if (!hasSufficientFunds(guildId, amount, includeFee = false)) {
            return false
        }

        val transaction = BankTransaction(
            guildId = guildId,
            actorId = UUID(0, 0), // System actor
            type = TransactionType.DEDUCTION,
            amount = amount,
            description = reason
        )

        return bankRepository.recordTransaction(transaction)
    }

    override fun canWithdraw(playerId: UUID, guildId: UUID): Boolean {
        return rankService.hasPermission(playerId, guildId, RankPermission.WITHDRAW_FROM_BANK)
    }

    override fun canDeposit(playerId: UUID, guildId: UUID): Boolean {
        return rankService.hasPermission(playerId, guildId, RankPermission.DEPOSIT_TO_BANK)
    }

    override fun getTransactionHistory(guildId: UUID, limit: Int?): List<BankTransaction> {
        return bankRepository.getTransactionsForGuild(guildId, limit)
    }

    override fun getAuditLog(guildId: UUID, limit: Int?): List<BankAudit> {
        return bankRepository.getAuditForGuild(guildId, limit)
    }

    override fun getMemberContributions(guildId: UUID): List<MemberContribution> {
        val members = memberRepository.getByGuild(guildId)
        return members.map { member ->
            MemberContribution(
                playerId = member.playerId,
                playerName = null, // Would get from PlayerService
                totalDeposits = getPlayerDeposits(member.playerId, guildId),
                totalWithdrawals = getPlayerWithdrawals(member.playerId, guildId),
                transactionCount = 0, // Placeholder
                lastTransaction = null // Placeholder
            )
        }.sortedByDescending { it.netContribution }
    }

    override fun getPlayerDeposits(playerId: UUID, guildId: UUID): Int {
        return bankRepository.getPlayerTotalDeposits(playerId, guildId)
    }

    override fun getPlayerWithdrawals(playerId: UUID, guildId: UUID): Int {
        return bankRepository.getPlayerTotalWithdrawals(playerId, guildId)
    }

    override fun calculateWithdrawalFee(guildId: UUID, amount: Int): Int {
        return (amount * WITHDRAWAL_FEE_PERCENT) / 100
    }

    override fun getMaxWithdrawalAmount(guildId: UUID, playerId: UUID): Int {
        val balance = getBalance(guildId)
        return if (canWithdraw(playerId, guildId)) balance else 0
    }

    override fun getMinDepositAmount(): Int = MIN_DEPOSIT

    override fun getMaxDepositAmount(): Int = MAX_DEPOSIT

    override fun hasSufficientFunds(guildId: UUID, amount: Int, includeFee: Boolean): Boolean {
        val balance = getBalance(guildId)
        val required = if (includeFee) {
            amount + calculateWithdrawalFee(guildId, amount)
        } else {
            amount
        }
        return balance >= required
    }

    override fun getBankStats(guildId: UUID): BankStats {
        val transactions = getTransactionHistory(guildId)
        val deposits = transactions.filter { it.type == TransactionType.DEPOSIT }.sumOf { it.amount }
        val withdrawals = transactions.filter { it.type == TransactionType.WITHDRAWAL }.sumOf { it.totalAmount }

        return BankStats(
            currentBalance = getBalance(guildId),
            totalDeposits = deposits,
            totalWithdrawals = withdrawals,
            totalTransactions = transactions.size,
            transactionVolume = deposits + withdrawals
        )
    }

    override fun processExpiredItems(): Int {
        // Placeholder for cleanup tasks
        return 0
    }

    override fun isValidAmount(amount: Int): Boolean {
        return amount in MIN_DEPOSIT..MAX_DEPOSIT
    }

    // Helper methods for audit logging
    private fun recordAuditDenied(guildId: UUID, playerId: UUID, action: String, reason: String) {
        val audit = BankAudit(
            guildId = guildId,
            actorId = playerId,
            action = AuditAction.PERMISSION_DENIED,
            details = "$action denied: $reason"
        )
        bankRepository.recordAudit(audit)
    }

    private fun recordAuditSuccess(guildId: UUID, playerId: UUID, action: String, amount: Int, oldBalance: Int, newBalance: Int) {
        val audit = BankAudit(
            guildId = guildId,
            actorId = playerId,
            action = when (action) {
                "DEPOSIT" -> AuditAction.DEPOSIT
                "WITHDRAWAL" -> AuditAction.WITHDRAWAL
                else -> AuditAction.BALANCE_CHECK
            },
            details = "$action of $amount completed",
            oldBalance = oldBalance,
            newBalance = newBalance
        )
        bankRepository.recordAudit(audit)
    }
}
