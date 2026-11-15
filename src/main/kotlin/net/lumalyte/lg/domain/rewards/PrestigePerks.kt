package net.lumalyte.lg.domain.rewards

import net.lumalyte.lg.config.PrestigePerksConfig

/**
 * REQ-180, REQ-181: the practical perks a guild has at a prestige tier. A `null`
 * tier means the tier could not be read, which gives no perk.
 */
class PrestigePerks(private val config: PrestigePerksConfig) {
    fun allyHomeSlots(tier: Int?): Int = if (reached(tier, config.secondAllyHomeTier)) 2 else 1

    fun extraGuildStalls(tier: Int?): Int = if (reached(tier, config.extraStallTier)) 1 else 0

    /** REQ-242: whether a guild at [tier] may hold a second vault. */
    fun secondVault(tier: Int?): Boolean = reached(tier, config.secondVaultTier)

    /** Perks that start at exactly [tier], in the order menus list them. */
    fun unlockedAt(tier: Int): List<PrestigePerk> = listOfNotNull(
        PrestigePerk.SECOND_ALLY_HOME.takeIf { tier == config.secondAllyHomeTier },
        PrestigePerk.EXTRA_STALL.takeIf { tier == config.extraStallTier },
        PrestigePerk.SECOND_VAULT.takeIf { tier == config.secondVaultTier },
    )

    private fun reached(tier: Int?, needed: Int): Boolean = tier != null && tier >= needed
}

enum class PrestigePerk { SECOND_ALLY_HOME, EXTRA_STALL, SECOND_VAULT }
