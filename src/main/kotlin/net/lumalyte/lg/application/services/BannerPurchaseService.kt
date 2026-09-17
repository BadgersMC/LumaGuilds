package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.BannerPurchaseRepository
import net.lumalyte.lg.domain.gold.*
import java.util.UUID

sealed interface BannerPurchaseResult {
    data class Completed(val amount: Long) : BannerPurchaseResult
    data object Rejected : BannerPurchaseResult
    data class Pending(val transactionId: UUID) : BannerPurchaseResult
}

class BannerPurchaseService(private val purchases: BannerPurchaseRepository, private val gold: GuildGoldService) {
    @Synchronized
    fun purchase(guildId: UUID, playerId: UUID, amount: Long, banner: String, deliver: (String) -> Unit): BannerPurchaseResult {
        val purchase = purchases.acquire(guildId, playerId, amount, banner)
        return try {
            if (purchase.phase == "PENDING") {
                val expected = GuildGoldMutation(purchase.id, guildId, playerId, GuildGoldRoute.SYSTEM,
                    GuildGoldDirection.DEBIT, purchase.amount, 0, "Banner copy purchase")
                val recorded = gold.operation(purchase.id)
                if (recorded != null && recorded.mutation != expected) return BannerPurchaseResult.Pending(purchase.id)
                val paid = if (recorded?.status == GuildGoldOperationStatus.APPLIED) true else {
                    when (gold.debitSystem(purchase.id, guildId, playerId, purchase.amount, "Banner copy purchase")) {
                        is GuildGoldResult.Applied -> true
                        is GuildGoldResult.Rejected -> {
                            if (purchases.transition(purchase.id, "PENDING", "REJECTED")) return BannerPurchaseResult.Rejected
                            false
                        }
                        else -> false
                    }
                }
                if (!paid || !purchases.transition(purchase.id, "PENDING", "PAID")) return BannerPurchaseResult.Pending(purchase.id)
            } else if (purchase.phase != "PAID") return BannerPurchaseResult.Pending(purchase.id)

            if (!purchases.transition(purchase.id, "PAID", "DELIVERING")) return BannerPurchaseResult.Pending(purchase.id)
            deliver(purchase.banner)
            if (purchases.transition(purchase.id, "DELIVERING", "COMPLETE")) BannerPurchaseResult.Completed(purchase.amount)
            else BannerPurchaseResult.Pending(purchase.id)
        } catch (_: Exception) {
            // Inventory or SQL may already have changed. Never retry an ambiguous delivery.
            BannerPurchaseResult.Pending(purchase.id)
        }
    }
}
