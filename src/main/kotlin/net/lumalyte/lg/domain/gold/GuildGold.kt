package net.lumalyte.lg.domain.gold

import java.util.UUID
import kotlin.math.floor
import kotlin.math.min

@JvmInline
value class GuildGoldAmount(val value: Long) {
    init {
        require(value >= 0) { "Guild gold amount cannot be negative" }
    }
}

enum class GuildGoldRoute {
    PERSONAL_ACCOUNT,
    PHYSICAL_ITEM,
    INTEREST,
    ADMIN,
    SYSTEM
}

enum class GuildGoldDirection {
    CREDIT,
    DEBIT
}

data class GuildGoldPolicy(
    val minDeposit: Long,
    val maxDeposit: Long,
    val withdrawalPercent: Double,
    val dailyWithdrawalLimit: Long,
    val depositFeePercent: Double,
    val withdrawalFeePercent: Double,
    val maxDepositFee: Long,
    val maxWithdrawalFee: Long,
    val globalCapacity: Long,
    val suspiciousThreshold: Long,
    val autoFreezeSuspicious: Boolean
) {
    init {
        require(withdrawalPercent in 0.0..1.0) { "Withdrawal percent must be between zero and one" }
    }
}

data class GuildGoldCapacity(
    val tier: Long,
    val permanent: Long
)

data class GuildGoldMutation(
    val transactionId: UUID,
    val guildId: UUID,
    val actorId: UUID,
    val route: GuildGoldRoute,
    val direction: GuildGoldDirection,
    val amount: Long,
    val fee: Long,
    val description: String
)

enum class GuildGoldRejection {
    INVALID_AMOUNT,
    CAPACITY_EXCEEDED,
    INSUFFICIENT_FUNDS,
    DAILY_LIMIT,
    WITHDRAWAL_PERCENT,
    FROZEN,
    SUSPICIOUS_FROZEN,
    EXTERNAL_UNAVAILABLE,
    EXTERNAL_REJECTED,
    UNAUTHORIZED,
    DUPLICATE_PENDING
}

sealed interface GuildGoldResult {
    data class Applied(
        val transactionId: UUID,
        val oldBalance: Long,
        val newBalance: Long,
        val fee: Long
    ) : GuildGoldResult

    data class Rejected(val reason: GuildGoldRejection) : GuildGoldResult

    data class Failed(
        val transactionId: UUID,
        val compensationSucceeded: Boolean
    ) : GuildGoldResult
}

enum class GuildGoldOperationStatus {
    PREPARED,
    BALANCE_APPLIED,
    APPLIED,
    REJECTED,
    COMPENSATED,
    FAILED_COMPENSATION
}

data class GuildGoldOperationRecord(
    val mutation: GuildGoldMutation,
    val status: GuildGoldOperationStatus,
    val oldBalance: Long?,
    val newBalance: Long?,
    val rejection: GuildGoldRejection? = null
)

sealed interface GuildGoldPreparation {
    data class New(val record: GuildGoldOperationRecord) : GuildGoldPreparation
    data class Existing(val record: GuildGoldOperationRecord) : GuildGoldPreparation
    data class Pending(val transactionId: UUID) : GuildGoldPreparation
    data object FingerprintMismatch : GuildGoldPreparation
}

object GuildGoldCalculator {
    fun effectiveCapacity(policy: GuildGoldPolicy, capacity: GuildGoldCapacity): Long {
        val combined = if (Long.MAX_VALUE - capacity.tier < capacity.permanent) {
            Long.MAX_VALUE
        } else {
            capacity.tier + capacity.permanent
        }
        return min(policy.globalCapacity, combined)
    }

    fun depositFee(policy: GuildGoldPolicy, amount: Long): Long =
        percentageFee(amount, policy.depositFeePercent, policy.maxDepositFee)

    fun withdrawalFee(policy: GuildGoldPolicy, amount: Long): Long =
        percentageFee(amount, policy.withdrawalFeePercent, policy.maxWithdrawalFee)

    fun maximumWithdrawal(policy: GuildGoldPolicy, balance: Long, withdrawnToday: Long): Long {
        val percentageAllowance = floor(balance.toDouble() * policy.withdrawalPercent).toLong()
        val dailyAllowance = (policy.dailyWithdrawalLimit - withdrawnToday).coerceAtLeast(0)
        return min(percentageAllowance, dailyAllowance)
    }

    private fun percentageFee(amount: Long, percentage: Double, cap: Long): Long =
        min(floor(amount.toDouble() * percentage).toLong(), cap)
}
