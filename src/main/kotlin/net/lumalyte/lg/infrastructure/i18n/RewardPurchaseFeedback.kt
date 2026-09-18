package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.domain.rewards.*

fun LangService.purchaseFeedback(result: RewardPurchaseResult): Component {
    val lang = this
    return when (result) {
        is RewardPurchaseResult.Applied -> lang.msg("chapter_two_rewards.purchase.applied", "price" to result.price)
        is RewardPurchaseResult.Failed -> lang.msg("chapter_two_rewards.purchase.retry", "transaction" to result.transactionId)
        is RewardPurchaseResult.Rejected -> when (result.reason) {
            RewardPurchaseRejection.UNAUTHORIZED -> lang.msg("chapter_two_rewards.purchase.unauthorized")
            RewardPurchaseRejection.INSUFFICIENT_FUNDS -> lang.msg("chapter_two_rewards.purchase.insufficient")
            RewardPurchaseRejection.FROZEN, RewardPurchaseRejection.PENDING_GOLD -> lang.msg("chapter_two_rewards.purchase.bank_unavailable")
            RewardPurchaseRejection.STALE_STATE, RewardPurchaseRejection.PRICE_CHANGED -> lang.msg("chapter_two_rewards.purchase.changed")
            RewardPurchaseRejection.LOCKED, RewardPurchaseRejection.ALREADY_OWNED,
            RewardPurchaseRejection.NO_IMPROVEMENT, RewardPurchaseRejection.UNKNOWN_REWARD -> lang.msg("chapter_two_rewards.purchase.ineligible")
            RewardPurchaseRejection.UNAVAILABLE, RewardPurchaseRejection.UNINITIALIZED,
            RewardPurchaseRejection.ID_CONFLICT -> lang.msg("chapter_two_rewards.unavailable")
        }
    }
}
