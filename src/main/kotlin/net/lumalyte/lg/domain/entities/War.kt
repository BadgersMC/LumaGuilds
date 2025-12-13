package net.lumalyte.lg.domain.entities

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a war between two guilds.
 */
data class War(
    val id: UUID = UUID.randomUUID(),
    val declaringGuildId: UUID,
    val defendingGuildId: UUID,
    val declaredAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val duration: Duration = Duration.ofDays(7), // Default 7 days
    val status: WarStatus = WarStatus.DECLARED,
    val objectives: Set<WarObjective> = emptySet(),
    val winner: UUID? = null,
    val loser: UUID? = null,
    val peaceTerms: String? = null
) {
    init {
        require(declaringGuildId != defendingGuildId) { "Guild cannot declare war on itself" }
    }

    /**
     * Checks if the war is currently active.
     */
    val isActive: Boolean
        get() = status == WarStatus.ACTIVE

    /**
     * Checks if the war has ended.
     */
    val isEnded: Boolean
        get() = status == WarStatus.ENDED

    /**
     * Gets the remaining duration of the war.
     */
    val remainingDuration: Duration?
        get() = startedAt?.let { start ->
            val endTime = start.plus(duration)
            val now = Instant.now()
            if (now.isBefore(endTime)) {
                Duration.between(now, endTime)
            } else {
                Duration.ZERO
            }
        }

    /**
     * Checks if the war has expired.
     */
    val isExpired: Boolean
        get() = remainingDuration?.isNegative ?: false

    companion object {
        fun create(declaringGuildId: UUID, defendingGuildId: UUID, duration: Duration = Duration.ofDays(7)): War {
            return War(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                duration = duration
            )
        }
    }
}

/**
 * Represents the status of a war.
 */
enum class WarStatus {
    DECLARED,    // War declared but not yet accepted/started
    ACTIVE,      // War is currently active
    ENDED,       // War has ended (either by timeout, surrender, or peace)
    CANCELLED    // War was cancelled before starting
}

/**
 * Represents an objective in a war.
 */
data class WarObjective(
    val id: UUID = UUID.randomUUID(),
    val type: ObjectiveType,
    val targetValue: Int,
    val currentValue: Int = 0,
    val description: String,
    val completed: Boolean = false,
    val completedAt: Instant? = null
) {
    /**
     * Checks if the objective is completed.
     */
    val isCompleted: Boolean
        get() = currentValue >= targetValue

    /**
     * Gets the progress percentage (0.0 to 1.0).
     */
    val progressPercentage: Double
        get() = if (targetValue > 0) {
            (currentValue.toDouble() / targetValue.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}

/**
 * Types of war objectives.
 */
enum class ObjectiveType {
    KILLS,           // Kill X enemy players
    DEATHS,          // Lose X players
    CLAIMS_CAPTURED, // Capture X enemy claims
    CLAIMS_LOST,     // Lose X own claims
    TIME_SURVIVAL,   // Survive for X hours
    RESOURCE_THEFT   // Steal X resources
}

/**
 * Represents war statistics for tracking progress.
 */
data class WarStats(
    val warId: UUID,
    val declaringGuildKills: Int = 0,
    val defendingGuildKills: Int = 0,
    val declaringGuildDeaths: Int = 0,
    val defendingGuildDeaths: Int = 0,
    val claimsCaptured: Int = 0,
    val claimsLost: Int = 0,
    val resourcesStolen: Int = 0,
    val lastUpdated: Instant = Instant.now()
) {
    /**
     * Gets the kill ratio for the declaring guild.
     */
    val declaringKillRatio: Double
        get() = if (declaringGuildDeaths > 0) {
            declaringGuildKills.toDouble() / declaringGuildDeaths.toDouble()
        } else {
            declaringGuildKills.toDouble()
        }

    /**
     * Gets the kill ratio for the defending guild.
     */
    val defendingKillRatio: Double
        get() = if (defendingGuildDeaths > 0) {
            defendingGuildKills.toDouble() / defendingGuildDeaths.toDouble()
        } else {
            defendingGuildKills.toDouble()
        }
}

/**
 * Represents a war declaration request.
 */
data class WarDeclaration(
    val id: UUID = UUID.randomUUID(),
    val declaringGuildId: UUID,
    val defendingGuildId: UUID,
    val proposedDuration: Duration = Duration.ofDays(7),
    val objectives: Set<WarObjective> = emptySet(),
    val terms: String? = null,
    val wagerAmount: Int = 0, // Amount wagered by declaring guild (escrowed)
    val declaredAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plus(Duration.ofHours(24)), // 24 hour expiration
    val accepted: Boolean = false,
    val rejected: Boolean = false
) {
    /**
     * Checks if the declaration is still valid (not expired and not responded to).
     */
    val isValid: Boolean
        get() = !accepted && !rejected && Instant.now().isBefore(expiresAt)

    /**
     * Gets the remaining time before expiration.
     */
    val remainingTime: Duration
        get() = if (Instant.now().isBefore(expiresAt)) {
            Duration.between(Instant.now(), expiresAt)
        } else {
            Duration.ZERO
        }
}
