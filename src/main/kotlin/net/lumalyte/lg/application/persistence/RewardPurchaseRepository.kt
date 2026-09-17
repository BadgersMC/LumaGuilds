package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.RewardPurchaseRequest
import net.lumalyte.lg.domain.rewards.RewardPurchaseResult
import net.lumalyte.lg.domain.rewards.RewardPurchaseRejection

interface RewardPurchaseRepository {
    /** Recheck the guard under the account lock; replay a matching durable receipt first. */
    fun purchase(request: RewardPurchaseRequest, guard: () -> RewardPurchaseRejection?): RewardPurchaseResult
}
