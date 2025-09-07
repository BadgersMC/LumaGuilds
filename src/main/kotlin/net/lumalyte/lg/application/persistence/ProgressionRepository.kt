package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.application.services.ExperienceSource
import java.time.Instant
import java.util.UUID

/**
 * Repository interface for progression data persistence.
 */
interface ProgressionRepository {

    /**
     * Saves or updates guild progression data.
     *
     * @param progression The progression data to save.
     * @return true if successful, false otherwise.
     */
    fun saveGuildProgression(progression: GuildProgression): Boolean

    /**
     * Gets guild progression data.
     *
     * @param guildId The guild ID.
     * @return The progression data if found, null otherwise.
     */
    fun getGuildProgression(guildId: UUID): GuildProgression?

    /**
     * Records an experience transaction.
     *
     * @param transaction The transaction to record.
     * @return true if successful, false otherwise.
     */
    fun recordExperienceTransaction(transaction: ExperienceTransaction): Boolean

    /**
     * Gets experience transactions for a guild.
     *
     * @param guildId The guild ID.
     * @param limit The maximum number of transactions to return.
     * @return List of experience transactions.
     */
    fun getExperienceTransactions(guildId: UUID, limit: Int = 50): List<ExperienceTransaction>

    /**
     * Gets the total experience earned by a guild from a specific source.
     *
     * @param guildId The guild ID.
     * @param source The experience source.
     * @return The total experience from the source.
     */
    fun getExperienceFromSource(guildId: UUID, source: ExperienceSource): Int

    /**
     * Saves guild activity metrics.
     *
     * @param metrics The activity metrics to save.
     * @return true if successful, false otherwise.
     */
    fun saveActivityMetrics(metrics: GuildActivityMetrics): Boolean

    /**
     * Gets activity metrics for a guild.
     *
     * @param guildId The guild ID.
     * @return The activity metrics if found, null otherwise.
     */
    fun getActivityMetrics(guildId: UUID): GuildActivityMetrics?

    /**
     * Gets activity metrics for all guilds.
     *
     * @param limit The maximum number of results.
     * @return List of activity metrics.
     */
    fun getAllActivityMetrics(limit: Int = 1000): List<GuildActivityMetrics>

    /**
     * Updates a specific activity metric for a guild.
     *
     * @param guildId The guild ID.
     * @param metricType The type of metric to update.
     * @param value The new value.
     * @return true if successful, false otherwise.
     */
    fun updateActivityMetric(guildId: UUID, metricType: ActivityMetricType, value: Int): Boolean

    /**
     * Increments a specific activity metric for a guild.
     *
     * @param guildId The guild ID.
     * @param metricType The type of metric to increment.
     * @param amount The amount to increment by.
     * @return true if successful, false otherwise.
     */
    fun incrementActivityMetric(guildId: UUID, metricType: ActivityMetricType, amount: Int = 1): Boolean

    /**
     * Gets the top guilds by a specific activity metric.
     *
     * @param metricType The metric type.
     * @param limit The maximum number of results.
     * @return List of guilds with their metric values.
     */
    fun getTopGuildsByMetric(metricType: ActivityMetricType, limit: Int = 10): List<Pair<UUID, Int>>

    /**
     * Resets activity metrics for all guilds.
     *
     * @return The number of guilds that had their metrics reset.
     */
    fun resetAllActivityMetrics(): Int

    /**
     * Gets progression statistics.
     *
     * @return Overall progression statistics.
     */
    fun getProgressionStats(): ProgressionStats

    /**
     * Gets level distribution statistics.
     *
     * @return Map of level to count of guilds at that level.
     */
    fun getLevelDistribution(): Map<Int, Int>

    /**
     * Deletes old experience transactions beyond a certain age.
     *
     * @param maxAgeDays The maximum age in days.
     * @return The number of transactions deleted.
     */
    fun deleteOldTransactions(maxAgeDays: Int = 90): Int

    /**
     * Gets the average level of all guilds.
     *
     * @return The average level.
     */
    fun getAverageGuildLevel(): Double

    /**
     * Gets the highest level achieved by any guild.
     *
     * @return The highest level.
     */
    fun getHighestGuildLevel(): Int

    /**
     * Gets guilds that recently leveled up.
     *
     * @param since The timestamp to check from.
     * @param limit The maximum number of results.
     * @return List of guilds that leveled up recently.
     */
    fun getRecentLevelUps(since: Instant, limit: Int = 20): List<Pair<UUID, Int>>
}

/**
 * Types of activity metrics that can be tracked.
 */
enum class ActivityMetricType {
    MEMBER_COUNT,
    ACTIVE_MEMBERS,
    CLAIMS_OWNED,
    CLAIMS_CREATED_THIS_WEEK,
    KILLS_THIS_WEEK,
    DEATHS_THIS_WEEK,
    BANK_DEPOSITS_THIS_WEEK,
    RELATIONS_FORMED,
    WARS_PARTICIPATED
}

/**
 * Overall progression statistics.
 */
data class ProgressionStats(
    val totalGuilds: Int = 0,
    val averageLevel: Double = 1.0,
    val highestLevel: Int = 1,
    val totalExperienceAwarded: Long = 0,
    val totalLevelUps: Int = 0,
    val mostCommonLevel: Int = 1
)
