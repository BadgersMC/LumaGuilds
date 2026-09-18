package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.*
import java.util.UUID

/** Creates immutable quotes; only explicit confirmation enters the atomic gold use case. */
class GuildRewardPurchaseService(
    private val rewards: GuildRewardService,
    private val gold: GuildGoldService,
    private val enabled: () -> Boolean,
    private val authorized: (UUID, UUID) -> Boolean
) {
    fun quote(actorId: UUID, guildId: UUID, rewardId: String): RewardPurchaseRequest? = try {
        if (!enabled() || !authorized(actorId, guildId)) null else {
            val state = rewards.read(guildId) as? GuildRewardRead.Available
            val offer = state?.entitlements?.offers?.find { it.reward.id == rewardId }
            if (offer?.status != RewardOfferStatus.AVAILABLE) null else
                RewardPurchaseRequest(UUID.randomUUID(), guildId, actorId, rewardId, offer.reward.price, state.version)
        }
    } catch (_: Exception) { null }

    fun confirm(actorId: UUID, quote: RewardPurchaseRequest): RewardPurchaseResult = try {
        when {
            actorId != quote.actorId -> RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAUTHORIZED)
            !enabled() -> RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAVAILABLE)
            // Gold rechecks authority under its transaction; do not block historical receipt replay
            // by reading ownership here after the original transaction may already have committed.
            else -> gold.purchaseReward(quote)
        }
    } catch (_: Exception) { RewardPurchaseResult.Failed(quote.transactionId) }
}

class GuildRewardPurchaseAccess(private val members: MemberRepository, private val ranks: RankRepository) {
    fun allowed(actorId: UUID, guildId: UUID): Boolean {
        val member = members.getByPlayerAndGuild(actorId, guildId) ?: return false
        val rank = ranks.getById(member.rankId) ?: return false
        return rank.guildId == guildId && RankPermission.MANAGE_GUILD_SETTINGS in rank.permissions &&
            RankPermission.WITHDRAW_FROM_BANK in rank.permissions
    }
}
