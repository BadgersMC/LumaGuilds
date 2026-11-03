package net.lumalyte.lg.domain.rewards

import java.util.Collections

/** Guild-scoped ownership. Saved locations and activation payments are separate assets. */
class RewardOwnership(
    currentRun: Set<String> = emptySet(),
    permanent: Set<String> = emptySet(),
    val initialHomeCapacity: Int = 1,
    val prestigeCount: Int = 0,
    val baseHomeSlotsEarned: Int = 1,
    activeOutpostsByPrestige: Map<Int, String> = emptyMap(),
    /** Runtime projection only: unresolved historical home ownership must not be billed. */
    val homeOutpostAuditPending: Boolean = false
) {
    val currentRun: Set<String> = Collections.unmodifiableSet(currentRun.toSet())
    val permanent: Set<String> = Collections.unmodifiableSet(permanent.toSet())
    /** Only newly paid durable acquisitions count; legacy permanent reward IDs do not. */
    val activeOutpostsByPrestige: Map<Int, String> =
        Collections.unmodifiableMap(activeOutpostsByPrestige.toMap())

    init {
        require(initialHomeCapacity > 0)
        require(prestigeCount in 0..6)
        require(baseHomeSlotsEarned in 1..6)
        require(activeOutpostsByPrestige.keys.all { it in 1..prestigeCount })
        require(activeOutpostsByPrestige.values.toSet().size == activeOutpostsByPrestige.size)
    }

    fun copy(
        currentRun: Set<String> = this.currentRun,
        permanent: Set<String> = this.permanent,
        initialHomeCapacity: Int = this.initialHomeCapacity,
        prestigeCount: Int = this.prestigeCount,
        baseHomeSlotsEarned: Int = this.baseHomeSlotsEarned,
        activeOutpostsByPrestige: Map<Int, String> = this.activeOutpostsByPrestige,
        homeOutpostAuditPending: Boolean = this.homeOutpostAuditPending
    ) = RewardOwnership(currentRun, permanent, initialHomeCapacity, prestigeCount,
        baseHomeSlotsEarned, activeOutpostsByPrestige, homeOutpostAuditPending)
}

/** Chapter 1 progression slots are retained after a prestige level reset. */
fun earnedBaseHomeSlotsAtLevel(level: Int): Int = when {
    level >= 30 -> 6
    level >= 20 -> 4
    level >= 15 -> 3
    level >= 7 -> 2
    else -> 1
}

enum class RewardOfferStatus { LOCKED, AUDIT_PENDING, AVAILABLE, PURCHASED, PERMANENT, NO_IMPROVEMENT }
data class RewardOffer(val reward: RewardDefinition, val status: RewardOfferStatus)

data class RewardEntitlements(
    val currentRunBankCapacity: Long,
    val permanentBankCapacity: Long,
    val bankCapacity: Long,
    val homeCapacity: Int,
    val memberCapacity: Int,
    val homeCooldownMultiplier: Double,
    val withdrawalFeeMultiplier: Double,
    val allyHomes: Boolean,
    val offers: List<RewardOffer>,
    val prestigeChoices: List<RewardDefinition>
) {
    fun offer(id: String): RewardOffer = requireNotNull(offers.find { it.reward.id == id }) { "Unknown reward: $id" }
}

/** Pure resolution and eligibility; callers must validate authority and atomically pay/persist. */
class RewardEntitlementResolver(private val catalog: RewardCatalog) {
    fun resolve(
        level: Int,
        ownership: RewardOwnership,
        globalBankCeiling: Long = Long.MAX_VALUE,
        memberCapacity: Int = 50
    ): RewardEntitlements {
        require(globalBankCeiling > 0 && memberCapacity > 0)
        val tier = catalog.level(level)
        val permanent = ownership.permanent.map(::requireReward)
        val current = ownership.currentRun.map(::requireReward)
        require(current.none { it.permanentOnPurchase }) { "Home capacity must be persisted as permanent" }
        require(current.all { it.level <= level || it.id in ownership.permanent }) { "Unretained purchase above run level" }
        val selections = permanent.filterNot { it.permanentOnPurchase }
        require(selections.size == ownership.prestigeCount) { "Prestige count does not match permanent selections" }
        require(selections.all { it.prestigeEligible })
        require(selections.count { it.effect is RewardEffect.HomeCooldown } <= 1) { "Multiple permanent cooldown tiers" }
        require(selections.count { it.effect is RewardEffect.WithdrawalFee } <= 1) { "Multiple permanent fee tiers" }

        val active = (current + permanent).distinctBy { it.id }
        val permanentBank = permanent.bankCapacity()
        val currentBank = Math.addExact(tier.automaticBankCapacity, current.filterNot { it.id in ownership.permanent }.bankCapacity())
        val totalBank = Math.addExact(currentBank, permanentBank)
        // Old permanent Outpost IDs document historical purchases, not valid per-prestige grants.
        // Even the sixth prestige must succeed once the 16-home ceiling has been reached.
        val activeOutposts = ownership.activeOutpostsByPrestige
        require((ownership.permanent + activeOutposts.values).filter { id ->
            catalog.find(id)?.effect is RewardEffect.HomeCapacity
        }.all { id -> (requireReward(id).effect as RewardEffect.HomeCapacity).amount == 1 }) {
            "Outpost rewards must grant exactly one home"
        }
        require(activeOutposts.values.all { catalog.find(it)?.effect is RewardEffect.HomeCapacity }) {
            "Invalid paid Outpost acquisition"
        }
        val homeCapacity = minOf(16, Math.addExact(
            Math.addExact(ownership.baseHomeSlotsEarned, ownership.prestigeCount),
            activeOutposts.size
        ))
        val cooldown = active.mapNotNull { (it.effect as? RewardEffect.HomeCooldown)?.multiplier }.minOrNull() ?: 1.0
        val fee = active.mapNotNull { (it.effect as? RewardEffect.WithdrawalFee)?.multiplier }.minOrNull() ?: 1.0
        val offers = catalog.rewards.map { reward ->
            val status = when {
                reward.effect is RewardEffect.HomeCapacity && reward.id in activeOutposts.values ->
                    RewardOfferStatus.PERMANENT
                reward.effect is RewardEffect.HomeCapacity && reward.level > level ->
                    RewardOfferStatus.LOCKED
                reward.effect is RewardEffect.HomeCapacity &&
                    (ownership.prestigeCount == 0 || ownership.prestigeCount in activeOutposts) ->
                    RewardOfferStatus.LOCKED
                reward.effect is RewardEffect.HomeCapacity && homeCapacity >= 16 ->
                    RewardOfferStatus.NO_IMPROVEMENT
                reward.effect is RewardEffect.HomeCapacity && ownership.homeOutpostAuditPending ->
                    RewardOfferStatus.AUDIT_PENDING
                reward.effect is RewardEffect.HomeCapacity -> RewardOfferStatus.AVAILABLE
                reward.id in ownership.permanent -> RewardOfferStatus.PERMANENT
                reward.id in ownership.currentRun -> RewardOfferStatus.PURCHASED
                reward.level > level -> RewardOfferStatus.LOCKED
                reward.effect is RewardEffect.HomeCooldown && reward.effect.multiplier >= cooldown -> RewardOfferStatus.NO_IMPROVEMENT
                reward.effect is RewardEffect.WithdrawalFee && reward.effect.multiplier >= fee -> RewardOfferStatus.NO_IMPROVEMENT
                else -> RewardOfferStatus.AVAILABLE
            }
            RewardOffer(reward, status)
        }
        val choices = if (level != 100 || ownership.prestigeCount >= 6) emptyList() else current.filter { candidate ->
            candidate.prestigeEligible && candidate.id !in ownership.permanent && when (candidate.effect) {
                is RewardEffect.HomeCooldown -> selections.none { it.effect is RewardEffect.HomeCooldown }
                is RewardEffect.WithdrawalFee -> selections.none { it.effect is RewardEffect.WithdrawalFee }
                else -> true
            }
        }
        return RewardEntitlements(currentBank, permanentBank, minOf(globalBankCeiling, totalBank), homeCapacity,
            memberCapacity, cooldown, fee, active.any { it.effect == RewardEffect.AllyHomes },
            Collections.unmodifiableList(offers), Collections.unmodifiableList(choices))
    }

    /** Computes the ownership write; this method neither charges gold nor commits a purchase. */
    fun recordPurchase(level: Int, ownership: RewardOwnership, rewardId: String): RewardOwnership {
        val offer = resolve(level, ownership).offer(rewardId)
        require(offer.status == RewardOfferStatus.AVAILABLE) { "Reward is not available: ${offer.status}" }
        return when {
            offer.reward.effect is RewardEffect.HomeCapacity -> ownership.copy(
                activeOutpostsByPrestige = ownership.activeOutpostsByPrestige +
                    (ownership.prestigeCount to rewardId)
            )
            offer.reward.permanentOnPurchase -> ownership.copy(permanent = ownership.permanent + rewardId)
            else -> ownership.copy(currentRun = ownership.currentRun + rewardId)
        }
    }

    /** Persistence invariant only; level, authorization, payment and war guards belong to the use case. */
    fun validateTransition(old: RewardOwnership, next: RewardOwnership) {
        resolve(100, old)
        resolve(100, next)
        require(next.initialHomeCapacity == old.initialHomeCapacity) { "Cannot rewrite migrated home capacity" }
        require(next.baseHomeSlotsEarned >= old.baseHomeSlotsEarned) { "Cannot revoke earned base homes" }
        require(next.permanent.containsAll(old.permanent)) { "Cannot remove permanent rewards" }
        require(next.activeOutpostsByPrestige.entries.containsAll(old.activeOutpostsByPrestige.entries)) {
            "Cannot rewrite a paid Outpost acquisition"
        }
        val newOutposts = next.activeOutpostsByPrestige - old.activeOutpostsByPrestige.keys
        when (next.prestigeCount - old.prestigeCount) {
            0 -> {
                require(next.currentRun.containsAll(old.currentRun - next.permanent)) { "Cannot remove run purchases without prestige" }
                require((next.permanent - old.permanent).all { requireReward(it).permanentOnPurchase }) {
                    "Only home capacity becomes permanent outside prestige"
                }
                require(newOutposts.size <= 1 && newOutposts.keys.all { it == old.prestigeCount && it >= 1 }) {
                    "At most one newly paid Outpost in the current prestige"
                }
                require(newOutposts.values.all { requireReward(it).effect is RewardEffect.HomeCapacity })
            }
            1 -> {
                require(next.currentRun.isEmpty()) { "Prestige must clear all run purchases" }
                require(newOutposts.isEmpty()) { "Prestige cannot grant a paid Outpost" }
                val newPermanent = next.permanent - old.permanent
                require(newPermanent.size == 1) { "Prestige retains exactly one perk" }
                require(resolve(100, old).prestigeChoices.any { it.id == newPermanent.single() }) { "Invalid retained selection" }
            }
            else -> throw IllegalArgumentException("Invalid prestige count transition")
        }
    }

    private fun requireReward(id: String) = requireNotNull(catalog.find(id)) { "Unknown owned reward: $id" }

    private fun List<RewardDefinition>.bankCapacity(): Long = fold(0L) { amount, reward ->
        Math.addExact(amount, (reward.effect as? RewardEffect.BankCapacity)?.amount ?: 0)
    }
}
