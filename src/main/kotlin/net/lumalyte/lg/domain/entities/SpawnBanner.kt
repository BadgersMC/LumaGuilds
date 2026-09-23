package net.lumalyte.lg.domain.entities

import java.util.UUID

enum class SpawnBannerCategory(
    val leaderboardType: ExtendedLeaderboardType,
    val period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
) {
    LEVEL(ExtendedLeaderboardType.GUILD_LEVEL),
    KILLS(ExtendedLeaderboardType.GUILD_KILLS),
    DEATHS(ExtendedLeaderboardType.GUILD_DEATHS),
    WEALTH(ExtendedLeaderboardType.GUILD_BANK_BALANCE),
    CLAIMS(ExtendedLeaderboardType.GUILD_CLAIM_COUNT),
    MEMBERS(ExtendedLeaderboardType.GUILD_MEMBER_COUNT),
    WEEKLY_ACTIVITY(ExtendedLeaderboardType.WEEKLY_ACTIVITY, LeaderboardPeriod.WEEKLY);

    val commandName: String
        get() = name.lowercase()

    companion object {
        fun parse(value: String): SpawnBannerCategory? = when (value.lowercase()) {
            "level", "levels" -> LEVEL
            "kills", "kill" -> KILLS
            "deaths", "death" -> DEATHS
            "wealth", "bank", "bank_balance", "balance" -> WEALTH
            "claims", "claim", "claim_count" -> CLAIMS
            "members", "member", "member_count" -> MEMBERS
            "weekly", "weekly_activity", "activity" -> WEEKLY_ACTIVITY
            else -> null
        }
    }
}

data class SpawnBannerState(
    val bannerId: UUID,
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int,
    val rank: Int,
    val category: SpawnBannerCategory,
    val createdAt: Long,
)
