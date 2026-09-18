package net.lumalyte.lg.domain.rewards

import java.util.UUID

data class RewardPurchaseRequest(
    val transactionId: UUID,
    val guildId: UUID,
    val actorId: UUID,
    val rewardId: String,
    val quotedPrice: Long,
    val expectedVersion: Long
) {
    init {
        require(rewardId.length in 1..64 && rewardId.matches(Regex("[a-z][a-z0-9-]*")))
        require(quotedPrice > 0 && expectedVersion >= 0)
    }
}

enum class RewardPurchaseRejection {
    UNAVAILABLE, UNKNOWN_REWARD, PRICE_CHANGED, UNAUTHORIZED, UNINITIALIZED,
    STALE_STATE, LOCKED, ALREADY_OWNED, NO_IMPROVEMENT, FROZEN, PENDING_GOLD,
    INSUFFICIENT_FUNDS, ID_CONFLICT
}

sealed interface RewardPurchaseResult {
    data class Applied(val transactionId: UUID, val rewardId: String, val price: Long,
        val oldBalance: Long, val newBalance: Long, val ownershipVersion: Long) : RewardPurchaseResult
    data class Rejected(val reason: RewardPurchaseRejection) : RewardPurchaseResult
    /** Retry the identical transaction ID. Never infer that refund or a new charge is safe. */
    data class Failed(val transactionId: UUID) : RewardPurchaseResult
}
