package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a snapshot of leaderboard data for a specific period.
 *
 * @property id The unique identifier for the snapshot.
 * @property type The type of leaderboard this snapshot represents.
 * @property periodStart The timestamp when the period started.
 * @property periodEnd The timestamp when the period ended.
 * @property data The JSON-formatted leaderboard data.
 * @property createdAt The timestamp when the snapshot was created.
 */
data class LeaderboardSnapshot(
    val id: UUID,
    val type: LeaderboardType,
    val periodStart: Instant,
    val periodEnd: Instant,
    val data: String,
    val createdAt: Instant
) {
    init {
        require(periodStart.isBefore(periodEnd)) { "Period start must be before period end." }
    }
}

/**
 * Represents the type of leaderboard.
 */
enum class LeaderboardType {
    KILLS,
    LEVEL,
    WEEKLY_ACTIVITY
}

/**
 * Extended leaderboard types for more detailed categorization.
 */
enum class ExtendedLeaderboardType {
    GUILD_KILLS,
    GUILD_DEATHS,
    GUILD_LEVEL,
    GUILD_BANK_BALANCE,
    GUILD_CLAIM_COUNT,
    GUILD_MEMBER_COUNT,
    PLAYER_KILLS,
    PLAYER_DEATHS,
    PLAYER_KILL_STREAK,
    WEEKLY_ACTIVITY
}

/**
 * Types of entities that can be on leaderboards.
 */
enum class EntityType {
    GUILD,
    PLAYER
}

/**
 * Time periods for leaderboards.
 */
enum class LeaderboardPeriod {
    ALL_TIME,
    MONTHLY,
    WEEKLY,
    DAILY
}
