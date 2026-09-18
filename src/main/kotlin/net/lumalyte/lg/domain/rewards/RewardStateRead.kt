package net.lumalyte.lg.domain.rewards

/** Level and ownership from the same committed database snapshot. */
sealed interface RewardStateRead {
    data class Found(val level: Int, val snapshot: RewardOwnershipSnapshot) : RewardStateRead
    data object Missing : RewardStateRead
    data object Failed : RewardStateRead
}

sealed interface GuildRewardRead {
    data object Disabled : GuildRewardRead
    data object Unavailable : GuildRewardRead
    data class Available(val level: Int, val version: Long, val entitlements: RewardEntitlements) : GuildRewardRead
}

data class RewardReadSettings(val enabled: Boolean, val bankCeiling: Long, val memberCapacity: Int)

class RewardStateUnavailableException : IllegalStateException("Guild reward state is unavailable")
