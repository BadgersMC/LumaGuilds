package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a leaderboard entry.
 */
data class LeaderboardEntry(
    val id: UUID = UUID.randomUUID(),
    val leaderboardType: ExtendedLeaderboardType,
    val entityId: UUID, // Guild ID or Player ID
    val entityType: EntityType,
    val value: Double,
    val rank: Int,
    val period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
    val periodStart: Instant? = null,
    val periodEnd: Instant? = null,
    val lastUpdated: Instant = Instant.now()
)

// EntityType and LeaderboardPeriod are imported from LeaderboardSnapshot.kt

// ExtendedLeaderboardType, EntityType, and LeaderboardPeriod are defined in LeaderboardSnapshot.kt

/**
 * Represents a leaderboard with its entries.
 */
data class Leaderboard(
    val type: ExtendedLeaderboardType,
    val period: LeaderboardPeriod,
    val entries: List<LeaderboardEntry>,
    val generatedAt: Instant = Instant.now(),
    val nextReset: Instant? = null
) {
    /**
     * Gets the top entries (default top 10).
     */
    fun getTopEntries(limit: Int = 10): List<LeaderboardEntry> {
        return entries.sortedBy { it.rank }.take(limit)
    }

    /**
     * Gets the entry for a specific entity.
     */
    fun getEntry(entityId: UUID): LeaderboardEntry? {
        return entries.find { it.entityId == entityId }
    }

    /**
     * Gets the rank for a specific entity.
     */
    fun getRank(entityId: UUID): Int? {
        return getEntry(entityId)?.rank
    }
}

/**
 * Represents weekly activity data for guilds.
 */
data class WeeklyActivity(
    val guildId: UUID,
    val weekStart: Instant,
    val weekEnd: Instant,
    val kills: Int = 0,
    val deaths: Int = 0,
    val claimsCreated: Int = 0,
    val claimsDestroyed: Int = 0,
    val membersJoined: Int = 0,
    val membersLeft: Int = 0,
    val bankDeposits: Int = 0,
    val bankWithdrawals: Int = 0,
    val chatMessages: Int = 0,
    val partiesFormed: Int = 0,
    val lastUpdated: Instant = Instant.now()
) {
    /**
     * Calculates the total activity score for the week.
     */
    val totalScore: Int
        get() = kills * 10 +
                deaths * 5 +
                claimsCreated * 20 +
                claimsDestroyed * 15 +
                membersJoined * 25 +
                bankDeposits / 100 + // Scale down monetary values
                bankWithdrawals / 100 +
                chatMessages +
                partiesFormed * 30

    /**
     * Gets the activity score per member.
     */
    fun getActivityPerMember(memberCount: Int): Double {
        return if (memberCount > 0) {
            totalScore.toDouble() / memberCount.toDouble()
        } else {
            0.0
        }
    }
}

/**
 * Represents leaderboard configuration.
 */
data class LeaderboardConfig(
    val type: ExtendedLeaderboardType,
    val enabled: Boolean = true,
    val maxEntries: Int = 100,
    val updateIntervalMinutes: Int = 60,
    val resetSchedule: ResetSchedule = ResetSchedule.WEEKLY
)

/**
 * Reset schedule for leaderboards.
 */
enum class ResetSchedule {
    DAILY,
    WEEKLY,
    MONTHLY,
    NEVER
}
