package net.lumalyte.lg.domain.rewards

import java.util.Collections

/** Only approved, bounded effects can be represented; no currency or XP awards. */
sealed interface RewardEffect {
    data class BankCapacity(val amount: Long) : RewardEffect {
        init { require(amount > 0) }
    }
    data class HomeCapacity(val amount: Int) : RewardEffect {
        init { require(amount > 0) }
    }
    data class HomeCooldown(val multiplier: Double) : RewardEffect {
        init { require(multiplier.isFinite() && multiplier > 0 && multiplier <= 1) }
    }
    data class WithdrawalFee(val multiplier: Double) : RewardEffect {
        init { require(multiplier.isFinite() && multiplier > 0 && multiplier <= 1) }
    }
    data object AllyHomes : RewardEffect
}

enum class RewardKind { NUMERIC, MAJOR }

data class RewardDefinition(
    val id: String,
    val name: String,
    val level: Int,
    val kind: RewardKind,
    val price: Long,
    val effect: RewardEffect,
    val permanentOnPurchase: Boolean = effect is RewardEffect.HomeCapacity,
    val prestigeEligible: Boolean = effect !is RewardEffect.HomeCapacity
) {
    init {
        require(id.length <= 64 && id.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid reward ID: $id" }
        require(name.isNotBlank())
        require(level in 5..100 && level % 5 == 0)
        require(price > 0)
        require(permanentOnPurchase == (effect is RewardEffect.HomeCapacity))
        require(prestigeEligible == !permanentOnPurchase)
        require(when (kind) {
            RewardKind.NUMERIC -> effect is RewardEffect.BankCapacity ||
                effect is RewardEffect.HomeCooldown || effect is RewardEffect.WithdrawalFee
            RewardKind.MAJOR -> level % 10 == 0 &&
                (effect is RewardEffect.HomeCapacity || effect == RewardEffect.AllyHomes)
        }) { "Effect does not match checkpoint kind" }
    }
}

data class RewardLevel(val level: Int, val automaticBankCapacity: Long, val rewards: List<RewardDefinition>)

/** A validated snapshot, suitable for sharing across operations without caller mutation. */
class RewardCatalog(levels: List<RewardLevel>) {
    private val tiers: List<RewardLevel> = Collections.unmodifiableList(levels.map {
        it.copy(rewards = Collections.unmodifiableList(it.rewards.toList()))
    }.sortedBy { it.level })
    val rewards: List<RewardDefinition> = Collections.unmodifiableList(tiers.flatMap { it.rewards })
    private val byId = rewards.associateBy { it.id }

    init {
        require(tiers.map { it.level } == (1..100).toList()) { "Catalog must cover exactly levels 1–100" }
        require(byId.size == rewards.size) { "Duplicate reward ID" }
        tiers.forEachIndexed { index, tier ->
            require(tier.automaticBankCapacity > 0)
            if (index > 0) {
                val previous = tiers[index - 1].automaticBankCapacity
                require(if (tier.level % 5 == 0) tier.automaticBankCapacity == previous
                    else tier.automaticBankCapacity > previous) { "Invalid automatic capacity cadence at ${tier.level}" }
            }
            val expectedKinds = when {
                tier.level % 10 == 0 -> listOf(RewardKind.NUMERIC, RewardKind.MAJOR)
                tier.level % 5 == 0 -> listOf(RewardKind.NUMERIC)
                else -> emptyList()
            }
            require(tier.rewards.map { it.kind } == expectedKinds) { "Invalid checkpoint rewards at ${tier.level}" }
            require(tier.rewards.all { it.level == tier.level && it.price <= tier.automaticBankCapacity })
        }
    }

    fun level(level: Int): RewardLevel {
        require(level in 1..100) { "Seasonal display levels are not reward levels" }
        return tiers[level - 1]
    }

    fun find(id: String): RewardDefinition? = byId[id]

    companion object {
        /** Operator-approved LG-1202 values, 2026-09-17. Not the legacy reward YAML. */
        fun chapterTwo(): RewardCatalog {
            var home = 0
            return RewardCatalog((1..100).map { level ->
                val rewards = buildList {
                    if (level % 5 == 0) {
                        val price = 100L * (level / 5)
                        add(when {
                            level % 10 != 0 -> {
                                val n = (level + 5) / 10
                                RewardDefinition("bank-$n", "Bank Extension $n", level, RewardKind.NUMERIC, price,
                                    RewardEffect.BankCapacity(1_000))
                            }
                            level <= 50 -> {
                                val n = level / 10
                                RewardDefinition("cooldown-$n", "Quick Travel $n", level, RewardKind.NUMERIC, price,
                                    RewardEffect.HomeCooldown((10 - n) / 10.0))
                            }
                            else -> {
                                val n = (level - 50) / 10
                                RewardDefinition("fee-$n", "Treasury Discount $n", level, RewardKind.NUMERIC, price,
                                    RewardEffect.WithdrawalFee((10 - n) / 10.0))
                            }
                        })
                    }
                    if (level % 10 == 0) {
                        val price = 500L * (level / 10)
                        add(if (level == 50) {
                            RewardDefinition("ally-homes", "Allied Waypoints", level, RewardKind.MAJOR, price, RewardEffect.AllyHomes)
                        } else {
                            home++
                            RewardDefinition("home-$home", "Guild Outpost $home", level, RewardKind.MAJOR, price,
                                RewardEffect.HomeCapacity(1))
                        })
                    }
                }
                RewardLevel(level, 8_000L + 500L * (level - level / 5 - 1), rewards)
            })
        }
    }
}
