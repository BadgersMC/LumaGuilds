package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import java.time.Instant
import java.util.UUID

/**
 * Represents a player kill in the combat system.
 */
data class Kill(
    val id: UUID = UUID.randomUUID(),
    val killerId: UUID,
    val victimId: UUID,
    val killerGuildId: UUID? = null,
    val victimGuildId: UUID? = null,
    val timestamp: Instant = Instant.now(),
    val weapon: String? = null,
    val worldId: UUID? = null,
    val location: Position3D? = null
) {
    init {
        require(killerId != victimId) { "Player cannot kill themselves" }
    }

    /**
     * Checks if this kill is between different guilds.
     */
    val isInterGuildKill: Boolean
        get() = killerGuildId != null && victimGuildId != null && killerGuildId != victimGuildId

    /**
     * Checks if this kill is between players in the same guild.
     */
    val isIntraGuildKill: Boolean
        get() = killerGuildId != null && victimGuildId != null && killerGuildId == victimGuildId
}

/**
 * Represents kill statistics for a guild.
 */
data class GuildKillStats(
    val guildId: UUID,
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val netKills: Int = 0,
    val killDeathRatio: Double = 0.0,
    val lastUpdated: Instant = Instant.now()
) {
    companion object {
        fun calculateNetKills(kills: Int, deaths: Int): Int = kills - deaths

        fun calculateKillDeathRatio(kills: Int, deaths: Int): Double {
            return if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths.toDouble()
        }
    }
}

/**
 * Represents kill statistics for a player.
 */
data class PlayerKillStats(
    val playerId: UUID,
    val guildId: UUID? = null,
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastKillTime: Instant? = null,
    val lastDeathTime: Instant? = null
)

/**
 * Anti-farm protection data for a player.
 */
data class AntiFarmData(
    val playerId: UUID,
    val recentKills: List<Instant> = emptyList(),
    val farmScore: Double = 0.0,
    val lastFarmCheck: Instant = Instant.now(),
    val isCurrentlyFarming: Boolean = false
) {
    /**
     * Checks if a new kill would be considered farming based on timing.
     */
    fun wouldBeFarming(newKillTime: Instant, minIntervalMs: Long = 10000): Boolean {
        if (recentKills.isEmpty()) return false

        val timeSinceLastKill = newKillTime.toEpochMilli() - recentKills.last().toEpochMilli()
        return timeSinceLastKill < minIntervalMs
    }

    /**
     * Adds a new kill to the recent kills list and updates farm score.
     */
    fun addKill(killTime: Instant, maxRecentKills: Int = 10): AntiFarmData {
        val updatedKills = (recentKills + killTime).takeLast(maxRecentKills)
        val newFarmScore = calculateFarmScore(updatedKills)
        val isFarming = newFarmScore > 0.7 // Threshold for farming detection

        return copy(
            recentKills = updatedKills,
            farmScore = newFarmScore,
            lastFarmCheck = killTime,
            isCurrentlyFarming = isFarming
        )
    }

    private fun calculateFarmScore(kills: List<Instant>): Double {
        if (kills.size < 3) return 0.0

        // Calculate average interval between kills
        val intervals = kills.zipWithNext { a, b -> b.toEpochMilli() - a.toEpochMilli() }
        val avgInterval = intervals.average()

        // Lower average interval = higher farm score
        val baseFarmScore = when {
            avgInterval < 5000 -> 1.0   // Very fast kills = definite farming
            avgInterval < 10000 -> 0.8  // Fast kills = likely farming
            avgInterval < 20000 -> 0.5  // Moderate kills = possible farming
            else -> 0.0                  // Normal kills = not farming
        }

        // Factor in kill frequency
        val frequencyScore = when (kills.size) {
            in 5..7 -> 0.2
            in 8..10 -> 0.4
            else -> 0.0
        }

        return (baseFarmScore + frequencyScore).coerceIn(0.0, 1.0)
    }
}
