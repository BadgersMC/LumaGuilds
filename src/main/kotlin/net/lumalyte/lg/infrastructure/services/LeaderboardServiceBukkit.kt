package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.services.ActivityType
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.events.GuildLeaderboardRankChangeEvent
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class LeaderboardServiceBukkit(
    private val repository: LeaderboardRepository
) : LeaderboardService {

    private val logger = LoggerFactory.getLogger(LeaderboardServiceBukkit::class.java)

    // --- Type mapping ---

    private fun LeaderboardType.toExtended(): ExtendedLeaderboardType = when (this) {
        LeaderboardType.KILLS -> ExtendedLeaderboardType.GUILD_KILLS
        LeaderboardType.LEVEL -> ExtendedLeaderboardType.GUILD_LEVEL
        LeaderboardType.WEEKLY_ACTIVITY -> ExtendedLeaderboardType.WEEKLY_ACTIVITY
    }

    private fun ExtendedLeaderboardType.toSimple(): LeaderboardType = when (this) {
        ExtendedLeaderboardType.GUILD_KILLS -> LeaderboardType.KILLS
        ExtendedLeaderboardType.GUILD_DEATHS -> LeaderboardType.KILLS
        ExtendedLeaderboardType.GUILD_LEVEL -> LeaderboardType.LEVEL
        ExtendedLeaderboardType.GUILD_BANK_BALANCE -> LeaderboardType.LEVEL
        ExtendedLeaderboardType.GUILD_CLAIM_COUNT -> LeaderboardType.LEVEL
        ExtendedLeaderboardType.GUILD_MEMBER_COUNT -> LeaderboardType.LEVEL
        ExtendedLeaderboardType.PLAYER_KILLS -> LeaderboardType.KILLS
        ExtendedLeaderboardType.PLAYER_DEATHS -> LeaderboardType.KILLS
        ExtendedLeaderboardType.PLAYER_KILL_STREAK -> LeaderboardType.KILLS
        ExtendedLeaderboardType.WEEKLY_ACTIVITY -> LeaderboardType.WEEKLY_ACTIVITY
    }

    // --- Leaderboard queries ---

    override fun getLeaderboard(type: LeaderboardType, period: LeaderboardPeriod): Leaderboard? {
        return try {
            val extended = type.toExtended()
            val entries = repository.getLeaderboardEntries(extended, period)
            if (entries.isEmpty()) return null
            Leaderboard(
                type = extended,
                period = period,
                entries = entries.sortedBy { it.rank },
                nextReset = getNextResetTime(type, period)
            )
        } catch (e: Exception) {
            logger.error("Failed to get leaderboard type=$type period=$period", e)
            null
        }
    }

    override fun getLeaderboardPage(
        type: LeaderboardType,
        period: LeaderboardPeriod,
        page: Int,
        pageSize: Int
    ): List<LeaderboardEntry> {
        return try {
            repository.getLeaderboardEntriesPaged(type.toExtended(), period, page * pageSize, pageSize)
        } catch (e: Exception) {
            logger.error("Failed to get leaderboard page type=$type page=$page", e)
            emptyList()
        }
    }

    override fun getEntityRank(type: LeaderboardType, entityId: UUID, period: LeaderboardPeriod): Int? {
        return try {
            repository.getEntityRank(type.toExtended(), entityId, period)
        } catch (e: Exception) {
            logger.error("Failed to get entity rank type=$type entity=$entityId", e)
            null
        }
    }

    override fun getEntityEntry(type: LeaderboardType, entityId: UUID, period: LeaderboardPeriod): LeaderboardEntry? {
        return try {
            repository.getLeaderboardEntry(type.toExtended(), entityId, period)
        } catch (e: Exception) {
            logger.error("Failed to get entity entry type=$type entity=$entityId", e)
            null
        }
    }

    override fun getTopEntities(type: LeaderboardType, period: LeaderboardPeriod, limit: Int): List<LeaderboardEntry> {
        return try {
            repository.getLeaderboardEntriesPaged(type.toExtended(), period, 0, limit)
        } catch (e: Exception) {
            logger.error("Failed to get top entities type=$type", e)
            emptyList()
        }
    }

    // --- Updates ---

    override fun updateEntityValue(
        type: LeaderboardType,
        entityId: UUID,
        newValue: Double,
        period: LeaderboardPeriod
    ): Boolean {
        return try {
            val extended = type.toExtended()

            // Capture old rank before update
            val oldRank = repository.getEntityRank(extended, entityId, period)

            // Determine entity type from extended type
            val entityType = when (extended) {
                ExtendedLeaderboardType.PLAYER_KILLS,
                ExtendedLeaderboardType.PLAYER_DEATHS,
                ExtendedLeaderboardType.PLAYER_KILL_STREAK -> EntityType.PLAYER
                else -> EntityType.GUILD
            }

            val existing = repository.getLeaderboardEntry(extended, entityId, period)
            val entry = existing?.copy(value = newValue, lastUpdated = Instant.now())
                ?: LeaderboardEntry(
                    leaderboardType = extended,
                    entityId = entityId,
                    entityType = entityType,
                    value = newValue,
                    rank = 0, // Will be recalculated on refresh
                    period = period
                )

            val saved = repository.saveLeaderboardEntry(entry)
            if (!saved) return false

            // Recalculate ranks for this leaderboard
            recalculateRanks(extended, period)

            // Check for rank change and fire event if guild
            if (entityType == EntityType.GUILD) {
                val newRank = repository.getEntityRank(extended, entityId, period)
                if (newRank != null && newRank != oldRank) {
                    Bukkit.getPluginManager().callEvent(
                        GuildLeaderboardRankChangeEvent(entityId, extended, period, oldRank, newRank)
                    )
                }
            }

            true
        } catch (e: Exception) {
            logger.error("Failed to update entity value type=$type entity=$entityId value=$newValue", e)
            false
        }
    }

    // --- Refresh ---

    override fun refreshLeaderboard(type: LeaderboardType, period: LeaderboardPeriod): Boolean {
        return try {
            recalculateRanks(type.toExtended(), period)
            true
        } catch (e: Exception) {
            logger.error("Failed to refresh leaderboard type=$type period=$period", e)
            false
        }
    }

    override fun refreshAllLeaderboards(): Int {
        var count = 0
        for (type in LeaderboardType.entries) {
            for (period in LeaderboardPeriod.entries) {
                if (refreshLeaderboard(type, period)) count++
            }
        }
        return count
    }

    // --- Weekly activity ---

    override fun getWeeklyActivity(guildId: UUID, weekStart: Instant): WeeklyActivity? {
        return try {
            repository.getWeeklyActivity(guildId, weekStart)
        } catch (e: Exception) {
            logger.error("Failed to get weekly activity guild=$guildId", e)
            null
        }
    }

    override fun updateWeeklyActivity(activity: WeeklyActivity): Boolean {
        return try {
            repository.saveWeeklyActivity(activity)
        } catch (e: Exception) {
            logger.error("Failed to update weekly activity guild=${activity.guildId}", e)
            false
        }
    }

    override fun recordActivity(guildId: UUID, activityType: ActivityType, value: Int): Boolean {
        return try {
            val weekStart = getCurrentWeekStart()
            val weekEnd = weekStart.plus(7, ChronoUnit.DAYS)

            val existing = repository.getWeeklyActivity(guildId, weekStart)
                ?: WeeklyActivity(guildId = guildId, weekStart = weekStart, weekEnd = weekEnd)

            val updated = when (activityType) {
                ActivityType.KILL -> existing.copy(kills = existing.kills + value, lastUpdated = Instant.now())
                ActivityType.DEATH -> existing.copy(deaths = existing.deaths + value, lastUpdated = Instant.now())
                ActivityType.CLAIM_CREATED -> existing.copy(claimsCreated = existing.claimsCreated + value, lastUpdated = Instant.now())
                ActivityType.CLAIM_DESTROYED -> existing.copy(claimsDestroyed = existing.claimsDestroyed + value, lastUpdated = Instant.now())
                ActivityType.MEMBER_JOINED -> existing.copy(membersJoined = existing.membersJoined + value, lastUpdated = Instant.now())
                ActivityType.MEMBER_LEFT -> existing.copy(membersLeft = existing.membersLeft + value, lastUpdated = Instant.now())
                ActivityType.BANK_DEPOSIT -> existing.copy(bankDeposits = existing.bankDeposits + value, lastUpdated = Instant.now())
                ActivityType.BANK_WITHDRAWAL -> existing.copy(bankWithdrawals = existing.bankWithdrawals + value, lastUpdated = Instant.now())
                ActivityType.CHAT_MESSAGE -> existing.copy(chatMessages = existing.chatMessages + value, lastUpdated = Instant.now())
                ActivityType.PARTY_FORMED -> existing.copy(partiesFormed = existing.partiesFormed + value, lastUpdated = Instant.now())
                ActivityType.RELATION_CHANGED -> existing // No dedicated field, just record
                ActivityType.WAR_DECLARED -> existing // No dedicated field
                ActivityType.WAR_ENDED -> existing // No dedicated field
            }

            repository.saveWeeklyActivity(updated)
        } catch (e: Exception) {
            logger.error("Failed to record activity guild=$guildId type=$activityType", e)
            false
        }
    }

    // --- Resets ---

    override fun resetLeaderboard(type: LeaderboardType, period: LeaderboardPeriod): Boolean {
        return try {
            repository.resetLeaderboardPeriod(type.toExtended(), period, Instant.now())
        } catch (e: Exception) {
            logger.error("Failed to reset leaderboard type=$type period=$period", e)
            false
        }
    }

    override fun processScheduledResets(): Int {
        var resetCount = 0
        try {
            for (extType in ExtendedLeaderboardType.entries) {
                val config = repository.getLeaderboardConfig(extType) ?: continue
                if (!config.enabled) continue

                for (period in LeaderboardPeriod.entries) {
                    if (period == LeaderboardPeriod.ALL_TIME) continue
                    if (shouldReset(config.resetSchedule, period)) {
                        // Snapshot before reset
                        createLeaderboardSnapshot(extType.toSimple(), period)
                        repository.resetLeaderboardPeriod(extType, period, Instant.now())
                        resetCount++
                        logger.info("Reset leaderboard type=$extType period=$period")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process scheduled resets", e)
        }
        return resetCount
    }

    override fun getNextResetTime(type: LeaderboardType, period: LeaderboardPeriod): Instant? {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        return when (period) {
            LeaderboardPeriod.ALL_TIME -> null
            LeaderboardPeriod.DAILY -> now.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant()
            LeaderboardPeriod.WEEKLY -> now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS).toInstant()
            LeaderboardPeriod.MONTHLY -> now.with(TemporalAdjusters.firstDayOfNextMonth())
                .truncatedTo(ChronoUnit.DAYS).toInstant()
        }
    }

    // --- Snapshots ---

    override fun getLeaderboardSnapshots(type: LeaderboardType, period: LeaderboardPeriod, limit: Int): List<LeaderboardSnapshot> {
        return try {
            repository.getLeaderboardSnapshots(type.toExtended(), period, limit)
        } catch (e: Exception) {
            logger.error("Failed to get snapshots type=$type period=$period", e)
            emptyList()
        }
    }

    override fun createLeaderboardSnapshot(type: LeaderboardType, period: LeaderboardPeriod): Boolean {
        return try {
            val extended = type.toExtended()
            val entries = repository.getLeaderboardEntries(extended, period)
            if (entries.isEmpty()) return false

            val now = Instant.now()
            val periodStart = getPeriodStart(period)

            // Serialize entries as simple JSON
            val data = entries.joinToString(",", "[", "]") { entry ->
                """{"entityId":"${entry.entityId}","rank":${entry.rank},"value":${entry.value}}"""
            }

            val snapshot = LeaderboardSnapshot(
                id = UUID.randomUUID(),
                type = type,
                periodStart = periodStart,
                periodEnd = now,
                data = data,
                createdAt = now
            )

            repository.saveLeaderboardSnapshot(snapshot)
        } catch (e: Exception) {
            logger.error("Failed to create snapshot type=$type period=$period", e)
            false
        }
    }

    // --- Config ---

    override fun isLeaderboardEnabled(type: LeaderboardType): Boolean {
        return try {
            val config = repository.getLeaderboardConfig(type.toExtended())
            config?.enabled ?: true // Default enabled
        } catch (e: Exception) {
            logger.error("Failed to check if leaderboard enabled type=$type", e)
            true
        }
    }

    override fun setLeaderboardEnabled(type: LeaderboardType, enabled: Boolean): Boolean {
        return try {
            val extended = type.toExtended()
            val existing = repository.getLeaderboardConfig(extended)
                ?: LeaderboardConfig(type = extended)
            repository.saveLeaderboardConfig(existing.copy(enabled = enabled))
        } catch (e: Exception) {
            logger.error("Failed to set leaderboard enabled type=$type enabled=$enabled", e)
            false
        }
    }

    override fun getLeaderboardConfig(type: LeaderboardType): LeaderboardConfig? {
        return try {
            repository.getLeaderboardConfig(type.toExtended())
        } catch (e: Exception) {
            logger.error("Failed to get leaderboard config type=$type", e)
            null
        }
    }

    override fun updateLeaderboardConfig(config: LeaderboardConfig): Boolean {
        return try {
            repository.saveLeaderboardConfig(config)
        } catch (e: Exception) {
            logger.error("Failed to update leaderboard config type=${config.type}", e)
            false
        }
    }

    // --- Internal helpers ---

    private fun recalculateRanks(type: ExtendedLeaderboardType, period: LeaderboardPeriod) {
        val entries = repository.getLeaderboardEntries(type, period)
        val ranked = entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        repository.batchUpdateEntries(ranked)
    }

    private fun shouldReset(schedule: ResetSchedule, period: LeaderboardPeriod): Boolean {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        return when (schedule) {
            ResetSchedule.NEVER -> false
            ResetSchedule.DAILY -> period == LeaderboardPeriod.DAILY && now.hour == 0 && now.minute < 5
            ResetSchedule.WEEKLY -> period == LeaderboardPeriod.WEEKLY && now.dayOfWeek == DayOfWeek.MONDAY && now.hour == 0 && now.minute < 5
            ResetSchedule.MONTHLY -> period == LeaderboardPeriod.MONTHLY && now.dayOfMonth == 1 && now.hour == 0 && now.minute < 5
        }
    }

    private fun getCurrentWeekStart(): Instant {
        return ZonedDateTime.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
    }

    private fun getPeriodStart(period: LeaderboardPeriod): Instant {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        return when (period) {
            LeaderboardPeriod.ALL_TIME -> Instant.EPOCH
            LeaderboardPeriod.DAILY -> now.truncatedTo(ChronoUnit.DAYS).toInstant()
            LeaderboardPeriod.WEEKLY -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS).toInstant()
            LeaderboardPeriod.MONTHLY -> now.withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS).toInstant()
        }
    }
}
