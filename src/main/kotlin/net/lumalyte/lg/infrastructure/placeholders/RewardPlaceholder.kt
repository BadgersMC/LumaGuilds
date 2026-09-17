package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.domain.rewards.GuildRewardRead

/** Unavailable numbers are blank, never a fabricated zero or default entitlement. */
object RewardPlaceholder {
    fun value(identifier: String, state: GuildRewardRead): String {
        if (identifier == "guild_reward_state") return when (state) {
            GuildRewardRead.Disabled -> "disabled"
            GuildRewardRead.Unavailable -> "unavailable"
            is GuildRewardRead.Available -> "available"
        }
        if (state !is GuildRewardRead.Available) return ""
        val rewards = state.entitlements
        return when (identifier) {
            "guild_reward_bank_capacity" -> rewards.bankCapacity.toString()
            "guild_reward_home_capacity" -> rewards.homeCapacity.toString()
            "guild_reward_member_capacity" -> rewards.memberCapacity.toString()
            "guild_reward_home_cooldown_multiplier" -> rewards.homeCooldownMultiplier.toString()
            "guild_reward_withdrawal_fee_multiplier" -> rewards.withdrawalFeeMultiplier.toString()
            "guild_reward_ally_homes" -> rewards.allyHomes.toString()
            else -> if (identifier.startsWith("guild_reward_offer_"))
                rewards.offers.find { it.reward.id == identifier.removePrefix("guild_reward_offer_") }?.status?.name ?: ""
                else ""
        }
    }
}
