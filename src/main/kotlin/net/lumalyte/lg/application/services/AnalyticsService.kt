package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BudgetCategory
import java.time.Instant
import java.util.UUID

/**
 * Service interface for guild analytics and statistics.
 */
interface AnalyticsService {

    /**
     * Gets guild performance metrics for a specific period.
     *
     * @param guildId The ID of the guild.
     * @param period The time period to analyze.
     * @return Guild performance metrics.
     */
    fun getGuildPerformanceMetrics(guildId: UUID, period: TimePeriod): GuildMetrics

    /**
     * Gets member activity analytics for a guild.
     *
     * @param guildId The ID of the guild.
     * @param period The time period to analyze.
     * @return Member activity analytics.
     */
    fun getMemberActivityAnalytics(guildId: UUID, period: TimePeriod): MemberActivityAnalytics

    /**
     * Gets bank analytics for a guild.
     *
     * @param guildId The ID of the guild.
     * @param period The time period to analyze.
     * @return Bank analytics data.
     */
    fun getBankAnalytics(guildId: UUID, period: TimePeriod): BankAnalyticsData

    /**
     * Gets war statistics for a guild.
     *
     * @param guildId The ID of the guild.
     * @param period The time period to analyze.
     * @return War statistics.
     */
    fun getWarStatistics(guildId: UUID, period: TimePeriod): WarStatistics

    /**
     * Generates comparative analysis between guilds.
     *
     * @param guildId The primary guild ID.
     * @param competitorIds List of competitor guild IDs.
     * @param period The time period to analyze.
     * @return Comparative analysis data.
     */
    fun generateComparativeAnalysis(guildId: UUID, competitorIds: List<UUID>, period: TimePeriod): ComparativeAnalysis

    /**
     * Gets trend analysis for a specific metric.
     *
     * @param guildId The ID of the guild.
     * @param metric The metric to analyze.
     * @param periods Number of periods to analyze.
     * @return Trend analysis data.
     */
    fun getTrendAnalysis(guildId: UUID, metric: MetricType, periods: Int): TrendData
}

/**
 * Time period for analytics.
 */
enum class TimePeriod {
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_YEAR,
    ALL_TIME
}

/**
 * Guild performance metrics.
 */
data class GuildMetrics(
    val guildId: UUID,
    val period: TimePeriod,
    val totalMembers: Int,
    val activeMembers: Int,
    val newMembers: Int,
    val totalBankBalance: Int,
    val monthlyBankGrowth: Double,
    val averageMemberContribution: Double,
    val warsWon: Int,
    val warsLost: Int,
    val alliancesCount: Int,
    val claimsHeld: Int,
    val activityScore: Double
)

/**
 * Member activity analytics.
 */
data class MemberActivityAnalytics(
    val guildId: UUID,
    val period: TimePeriod,
    val totalMembers: Int,
    val activeMembers: Int,
    val averageActivityScore: Double,
    val topContributors: List<MemberContributionData>,
    val inactiveMembers: Int,
    val activityDistribution: Map<ActivityLevel, Int>
)

/**
 * Bank analytics data.
 */
data class BankAnalyticsData(
    val guildId: UUID,
    val period: TimePeriod,
    val totalTransactions: Int,
    val totalDeposits: Int,
    val totalWithdrawals: Int,
    val netFlow: Int,
    val averageTransactionAmount: Double,
    val largestTransaction: Int,
    val budgetUtilization: Map<BudgetCategory, BudgetUtilization>,
    val peakTransactionHours: List<Int>
)

/**
 * Budget utilization data.
 */
data class BudgetUtilization(
    val category: BudgetCategory,
    val allocatedAmount: Int,
    val spentAmount: Int,
    val utilizationPercentage: Double,
    val status: net.lumalyte.lg.domain.entities.BudgetStatus
)

/**
 * War statistics.
 */
data class WarStatistics(
    val guildId: UUID,
    val period: TimePeriod,
    val warsParticipated: Int,
    val warsWon: Int,
    val warsLost: Int,
    val winRate: Double,
    val averageWarDuration: Long,
    val totalKills: Int,
    val totalDeaths: Int,
    val kdRatio: Double
)

/**
 * Comparative analysis between guilds.
 */
data class ComparativeAnalysis(
    val primaryGuildId: UUID,
    val competitorGuilds: List<UUID>,
    val metrics: Map<String, ComparisonData>
)

/**
 * Comparison data for a specific metric.
 */
data class ComparisonData(
    val primaryValue: Double,
    val competitorValues: Map<UUID, Double>,
    val rank: Int,
    val percentile: Double
)

/**
 * Trend analysis data.
 */
data class TrendData(
    val guildId: UUID,
    val metric: MetricType,
    val dataPoints: List<DataPoint>
)

/**
 * Data point for trend analysis.
 */
data class DataPoint(
    val timestamp: Instant,
    val value: Double
)

/**
 * Types of metrics for trend analysis.
 */
enum class MetricType {
    MEMBER_COUNT,
    BANK_BALANCE,
    ACTIVITY_SCORE,
    WAR_WINS,
    TRANSACTION_VOLUME
}

/**
 * Member contribution data.
 */
data class MemberContributionData(
    val memberId: UUID,
    val memberName: String,
    val totalContributions: Int,
    val totalWithdrawals: Int,
    val netContribution: Int,
    val transactionCount: Int,
    val rank: Int
)
