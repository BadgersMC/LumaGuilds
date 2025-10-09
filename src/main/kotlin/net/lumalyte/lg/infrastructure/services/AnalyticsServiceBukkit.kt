package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.GuildBudgetRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.BudgetCategory
import net.lumalyte.lg.domain.entities.BudgetStatus
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.domain.entities.War
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Bukkit implementation of AnalyticsService.
 */
class AnalyticsServiceBukkit(
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val bankRepository: BankRepository,
    private val budgetRepository: GuildBudgetRepository,
    private val warRepository: WarRepository
) : AnalyticsService {

    private val logger = LoggerFactory.getLogger(AnalyticsServiceBukkit::class.java)

    override fun getGuildPerformanceMetrics(guildId: UUID, period: TimePeriod): GuildMetrics {
        try {
            val guild = guildRepository.getById(guildId) ?: throw IllegalArgumentException("Guild $guildId not found")
            val periodStart = getPeriodStart(period)

            // Get member counts
            val allMembers = memberRepository.getByGuild(guildId)
            val newMembers = allMembers.count { it.joinedAt.isAfter(periodStart) }
            val activeMembers = allMembers.size // Simplified - in real implementation, track activity

            // Get bank data
            val transactions = bankRepository.getTransactionsForGuild(guildId)
            val periodTransactions = transactions.filter { transaction: BankTransaction -> transaction.timestamp.isAfter(periodStart) }

            val totalDeposits = periodTransactions.filter { transaction: BankTransaction -> transaction.type == TransactionType.DEPOSIT }.sumOf { transaction: BankTransaction -> transaction.amount }
            val totalWithdrawals = periodTransactions.filter { transaction: BankTransaction -> transaction.type == TransactionType.WITHDRAWAL }.sumOf { transaction: BankTransaction -> transaction.amount }
            val currentBalance = bankRepository.getGuildBalance(guildId)

            // Calculate growth rate (simplified)
            val monthlyBankGrowth = if (periodTransactions.isNotEmpty()) {
                val avgMonthly = periodTransactions.sumOf { transaction: BankTransaction -> transaction.amount } / periodTransactions.size.toDouble()
                (avgMonthly / currentBalance) * 100
            } else 0.0

            // Get war data
            val wars = warRepository.getWarsForGuild(guildId)
            val periodWars = wars.filter { war: War -> war.startedAt?.isAfter(periodStart) ?: false }
            val warsWon = periodWars.count { war: War -> war.winner == guildId }
            val warsLost = periodWars.count { war: War -> war.winner != null && war.winner != guildId }

            // Get alliances count
            val alliancesCount = 0 // Would need diplomacy repository

            // Get claims count
            val claimsHeld = 0 // Would need claims repository

            // Calculate activity score (simplified)
            val activityScore = calculateActivityScore(allMembers.toList(), periodTransactions)

            return GuildMetrics(
                guildId = guildId,
                period = period,
                totalMembers = allMembers.size,
                activeMembers = activeMembers,
                newMembers = newMembers,
                totalBankBalance = currentBalance,
                monthlyBankGrowth = monthlyBankGrowth,
                averageMemberContribution = if (allMembers.isNotEmpty()) totalDeposits.toDouble() / allMembers.size else 0.0,
                warsWon = warsWon,
                warsLost = warsLost,
                alliancesCount = alliancesCount,
                claimsHeld = claimsHeld,
                activityScore = activityScore
            )
        } catch (e: Exception) {
            logger.error("Error getting guild performance metrics for guild $guildId", e)
            throw e
        }
    }

    override fun getMemberActivityAnalytics(guildId: UUID, period: TimePeriod): MemberActivityAnalytics {
        try {
            val allMembers = memberRepository.getByGuild(guildId)
            val periodStart = getPeriodStart(period)

            // Calculate activity levels
            val activityDistribution = mutableMapOf<ActivityLevel, Int>()
            val topContributors = mutableListOf<MemberContributionData>()

            for (member in allMembers) {
                val memberTransactions = bankRepository.getTransactionsByPlayerId(member.playerId)
                    .filter { transaction: BankTransaction -> transaction.timestamp.isAfter(periodStart) }

                val contributions = memberTransactions.filter { transaction: BankTransaction -> transaction.type == TransactionType.DEPOSIT }.sumOf { transaction: BankTransaction -> transaction.amount }
                val withdrawals = memberTransactions.filter { transaction: BankTransaction -> transaction.type == TransactionType.WITHDRAWAL }.sumOf { transaction: BankTransaction -> transaction.amount }

                val activityLevel = when {
                    memberTransactions.size >= 5 -> ActivityLevel.HIGH
                    memberTransactions.size >= 2 -> ActivityLevel.MEDIUM
                    else -> ActivityLevel.LOW
                }

                activityDistribution[activityLevel] = activityDistribution.getOrDefault(activityLevel, 0) + 1

                topContributors.add(MemberContributionData(
                    memberId = member.playerId,
                    memberName = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown",
                    totalContributions = contributions,
                    totalWithdrawals = withdrawals,
                    netContribution = contributions - withdrawals,
                    transactionCount = memberTransactions.size,
                    rank = topContributors.size + 1
                ))
            }

            topContributors.sortByDescending { it.netContribution }

            val inactiveMembers = activityDistribution.getOrDefault(ActivityLevel.LOW, 0)
            val averageActivityScore = if (allMembers.isNotEmpty()) {
                allMembers.sumOf { calculateMemberActivityScore(it.playerId) }.toDouble() / allMembers.size
            } else 0.0

            return MemberActivityAnalytics(
                guildId = guildId,
                period = period,
                totalMembers = allMembers.size,
                activeMembers = allMembers.size - inactiveMembers,
                averageActivityScore = averageActivityScore,
                topContributors = topContributors.take(10),
                inactiveMembers = inactiveMembers,
                activityDistribution = activityDistribution
            )
        } catch (e: Exception) {
            logger.error("Error getting member activity analytics for guild $guildId", e)
            throw e
        }
    }

    override fun getBankAnalytics(guildId: UUID, period: TimePeriod): BankAnalyticsData {
        try {
            val periodStart = getPeriodStart(period)
            val transactions = bankRepository.getTransactionsForGuild(guildId)
                .filter { transaction: BankTransaction -> transaction.timestamp.isAfter(periodStart) }

            val deposits = transactions.filter { transaction: BankTransaction -> transaction.type == TransactionType.DEPOSIT }
            val withdrawals = transactions.filter { transaction: BankTransaction -> transaction.type == TransactionType.WITHDRAWAL }

            val totalDeposits = deposits.sumOf { transaction: BankTransaction -> transaction.amount }
            val totalWithdrawals = withdrawals.sumOf { transaction: BankTransaction -> transaction.amount }
            val netFlow = totalDeposits - totalWithdrawals

            val averageTransactionAmount = if (transactions.isNotEmpty()) {
                transactions.sumOf { transaction: BankTransaction -> transaction.amount }.toDouble() / transactions.size
            } else 0.0

            val largestTransaction = transactions.maxOfOrNull { transaction: BankTransaction -> transaction.amount } ?: 0

            // Calculate budget utilization
            val budgets = budgetRepository.findByGuildId(guildId)
            val budgetUtilization = mutableMapOf<BudgetCategory, BudgetUtilization>()

            for (budget in budgets) {
            val spentInPeriod = transactions
                .filter { transaction: BankTransaction -> transaction.timestamp.isAfter(budget.periodStart) && transaction.timestamp.isBefore(budget.periodEnd) }
                .filter { transaction: BankTransaction -> transaction.type == TransactionType.WITHDRAWAL }
                .sumOf { transaction: BankTransaction -> transaction.amount }

                val utilization = BudgetUtilization(
                    category = budget.category,
                    allocatedAmount = budget.allocatedAmount,
                    spentAmount = spentInPeriod,
                    utilizationPercentage = if (budget.allocatedAmount > 0) (spentInPeriod.toDouble() / budget.allocatedAmount) * 100 else 0.0,
                    status = budget.getStatus()
                )

                budgetUtilization[budget.category] = utilization
            }

            // Calculate peak transaction hours
            val hourCounts = transactions.groupBy { it.timestamp.atZone(java.time.ZoneOffset.UTC).hour }
            val peakHours = hourCounts.entries.sortedByDescending { it.value.size }.take(3).map { it.key }

            return BankAnalyticsData(
                guildId = guildId,
                period = period,
                totalTransactions = transactions.size,
                totalDeposits = totalDeposits,
                totalWithdrawals = totalWithdrawals,
                netFlow = netFlow,
                averageTransactionAmount = averageTransactionAmount,
                largestTransaction = largestTransaction,
                budgetUtilization = budgetUtilization,
                peakTransactionHours = peakHours
            )
        } catch (e: Exception) {
            logger.error("Error getting bank analytics for guild $guildId", e)
            throw e
        }
    }

    override fun getWarStatistics(guildId: UUID, period: TimePeriod): WarStatistics {
        try {
            val periodStart = getPeriodStart(period)
            val wars = warRepository.getWarsForGuild(guildId)
                .filter { war: War -> war.startedAt?.isAfter(periodStart) ?: false }

            val warsWon = wars.count { war: War -> war.winner == guildId }
            val warsLost = wars.count { war: War -> war.winner != null && war.winner != guildId }
            val winRate = if (wars.isNotEmpty()) (warsWon.toDouble() / wars.size) * 100 else 0.0

            val averageWarDuration = if (wars.isNotEmpty()) {
                wars.map { war: War -> ChronoUnit.HOURS.between(war.startedAt, war.endedAt ?: Instant.now()) }.average().roundToInt().toLong()
            } else 0L

            // Simplified - in real implementation, would track kills/deaths per war
            val totalKills = 0
            val totalDeaths = 0
            val kdRatio = if (totalDeaths > 0) totalKills.toDouble() / totalDeaths else 0.0

            return WarStatistics(
                guildId = guildId,
                period = period,
                warsParticipated = wars.size,
                warsWon = warsWon,
                warsLost = warsLost,
                winRate = winRate,
                averageWarDuration = averageWarDuration,
                totalKills = totalKills,
                totalDeaths = totalDeaths,
                kdRatio = kdRatio
            )
        } catch (e: Exception) {
            logger.error("Error getting war statistics for guild $guildId", e)
            throw e
        }
    }

    override fun generateComparativeAnalysis(guildId: UUID, competitorIds: List<UUID>, period: TimePeriod): ComparativeAnalysis {
        try {
            val allGuilds = listOf(guildId) + competitorIds
            val metrics = mutableMapOf<String, ComparisonData>()

            // Compare member counts
            val memberCounts = allGuilds.associateWith { memberRepository.getByGuild(it).size }
            val primaryMemberCount = memberCounts[guildId] ?: 0
            val competitorMemberCounts = competitorIds.associateWith { (memberCounts[it] ?: 0).toDouble() }

            metrics["member_count"] = ComparisonData(
                primaryValue = primaryMemberCount.toDouble(),
                competitorValues = competitorMemberCounts,
                rank = calculateRank(primaryMemberCount, competitorMemberCounts.values.map { it.toInt() }),
                percentile = calculatePercentile(primaryMemberCount, memberCounts.values)
            )

            // Compare bank balances
            val bankBalances = allGuilds.associateWith { bankRepository.getGuildBalance(it) }
            val primaryBalance = bankBalances[guildId] ?: 0
            val competitorBalances = competitorIds.associateWith { (bankBalances[it] ?: 0).toDouble() }

            metrics["bank_balance"] = ComparisonData(
                primaryValue = primaryBalance.toDouble(),
                competitorValues = competitorBalances,
                rank = calculateRank(primaryBalance, competitorBalances.values.map { it.toInt() }),
                percentile = calculatePercentile(primaryBalance, bankBalances.values)
            )

            return ComparativeAnalysis(
                primaryGuildId = guildId,
                competitorGuilds = competitorIds,
                metrics = metrics
            )
        } catch (e: Exception) {
            logger.error("Error generating comparative analysis for guild $guildId", e)
            throw e
        }
    }

    override fun getTrendAnalysis(guildId: UUID, metric: MetricType, periods: Int): TrendData {
        try {
            val dataPoints = mutableListOf<DataPoint>()
            val now = Instant.now()

            when (metric) {
                MetricType.MEMBER_COUNT -> {
                    for (i in 0 until periods) {
                        val periodStart = now.minus(i.toLong(), ChronoUnit.DAYS)
                        val memberCount = memberRepository.getByGuild(guildId).size
                        dataPoints.add(DataPoint(periodStart, memberCount.toDouble()))
                    }
                }
                MetricType.BANK_BALANCE -> {
                    for (i in 0 until periods) {
                        val periodStart = now.minus(i.toLong(), ChronoUnit.DAYS)
                        val balance = bankRepository.getGuildBalance(guildId) // Use current balance for trend analysis
                        dataPoints.add(DataPoint(periodStart, balance.toDouble()))
                    }
                }
                // Add other metrics as needed
                else -> {
                    // Simplified implementation
                    for (i in 0 until periods) {
                        val periodStart = now.minus(i.toLong(), ChronoUnit.DAYS)
                        dataPoints.add(DataPoint(periodStart, 0.0))
                    }
                }
            }

            return TrendData(
                guildId = guildId,
                metric = metric,
                dataPoints = dataPoints.sortedBy { it.timestamp }
            )
        } catch (e: Exception) {
            logger.error("Error getting trend analysis for guild $guildId, metric $metric", e)
            throw e
        }
    }

    // Helper methods

    private fun getPeriodStart(period: TimePeriod): Instant {
        return when (period) {
            TimePeriod.LAST_7_DAYS -> Instant.now().minus(7, ChronoUnit.DAYS)
            TimePeriod.LAST_30_DAYS -> Instant.now().minus(30, ChronoUnit.DAYS)
            TimePeriod.LAST_90_DAYS -> Instant.now().minus(90, ChronoUnit.DAYS)
            TimePeriod.LAST_YEAR -> Instant.now().minus(365, ChronoUnit.DAYS)
            TimePeriod.ALL_TIME -> Instant.EPOCH
        }
    }

    private fun calculateActivityScore(members: List<net.lumalyte.lg.domain.entities.Member>, transactions: List<net.lumalyte.lg.domain.entities.BankTransaction>): Double {
        if (members.isEmpty()) return 0.0

        val memberActivity = members.associate { member ->
            val memberTransactions = transactions.filter { transaction: BankTransaction -> transaction.actorId == member.playerId }
            val activityScore = when {
                memberTransactions.size >= 10 -> 100.0
                memberTransactions.size >= 5 -> 75.0
                memberTransactions.size >= 1 -> 50.0
                else -> 0.0
            }
            member.playerId to activityScore
        }

        return memberActivity.values.average()
    }

    private fun calculateMemberActivityScore(memberId: UUID): Int {
        // Simplified implementation
        return 50
    }

    private fun calculateRank(primaryValue: Int, competitorValues: Collection<Int>): Int {
        val allValues = competitorValues + primaryValue
        return allValues.sortedDescending().indexOf(primaryValue) + 1
    }

    private fun calculatePercentile(primaryValue: Int, allValues: Collection<Int>): Double {
        val sortedValues = allValues.sorted()
        val index = sortedValues.indexOf(primaryValue)
        return if (sortedValues.isNotEmpty()) ((index + 1).toDouble() / sortedValues.size) * 100 else 0.0
    }
}
