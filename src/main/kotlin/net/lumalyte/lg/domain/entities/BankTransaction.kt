package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a bank transaction for a guild.
 */
data class BankTransaction(
    val id: UUID = UUID.randomUUID(),
    val guildId: UUID,
    val actorId: UUID,
    val type: TransactionType,
    val amount: Int,
    val description: String? = null,
    val fee: Int = 0,
    val timestamp: Instant = Instant.now()
) {
    init {
        require(amount > 0) { "Transaction amount must be positive" }
        require(fee >= 0) { "Transaction fee cannot be negative" }
        require(amount + fee <= Int.MAX_VALUE) { "Total transaction value would overflow" }
    }

    /**
     * Gets the net amount of the transaction (amount + fee).
     */
    val totalAmount: Int
        get() = amount + fee

    companion object {
        fun deposit(guildId: UUID, actorId: UUID, amount: Int, description: String? = null): BankTransaction {
            return BankTransaction(
                guildId = guildId,
                actorId = actorId,
                type = TransactionType.DEPOSIT,
                amount = amount,
                description = description
            )
        }

        fun withdraw(guildId: UUID, actorId: UUID, amount: Int, fee: Int = 0, description: String? = null): BankTransaction {
            return BankTransaction(
                guildId = guildId,
                actorId = actorId,
                type = TransactionType.WITHDRAWAL,
                amount = amount,
                description = description,
                fee = fee
            )
        }
    }
}

/**
 * Represents a guild member's contribution summary to the bank.
 */
data class MemberContribution(
    val playerId: UUID,
    val playerName: String?,
    val totalDeposits: Int,
    val totalWithdrawals: Int,
    val transactionCount: Int,
    val lastTransaction: Instant?
) {
    /**
     * Gets the net contribution (deposits - withdrawals).
     * Positive values indicate net contributor, negative values indicate net user.
     */
    val netContribution: Int
        get() = totalDeposits - totalWithdrawals

    /**
     * Gets the contribution status based on net contribution.
     */
    val contributionStatus: ContributionStatus
        get() = when {
            netContribution > 0 -> ContributionStatus.CONTRIBUTOR
            netContribution < 0 -> ContributionStatus.FREELOADER
            totalDeposits > 0 -> ContributionStatus.BREAK_EVEN_CONTRIBUTOR
            else -> ContributionStatus.NEUTRAL
        }

    enum class ContributionStatus {
        CONTRIBUTOR,        // Net positive contribution
        FREELOADER,         // Net negative contribution
        BREAK_EVEN_CONTRIBUTOR, // Exactly break even but has deposited
        NEUTRAL             // No transactions
    }
}

/**
 * Types of bank transactions.
 */
enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    DEDUCTION
}

/**
 * Represents an audit entry for bank transactions.
 */
data class BankAudit(
    val id: UUID = UUID.randomUUID(),
    val transactionId: UUID? = null,
    val guildId: UUID,
    val actorId: UUID,
    val action: AuditAction,
    val details: String,
    val oldBalance: Int? = null,
    val newBalance: Int? = null,
    val timestamp: Instant = Instant.now()
)

/**
 * Types of audit actions.
 */
enum class AuditAction {
    DEPOSIT,
    WITHDRAWAL,
    BALANCE_CHECK,
    PERMISSION_DENIED,
    INSUFFICIENT_FUNDS,
    FEE_CHARGED,
    BANK_CREATED,
    BANK_RESET
}
