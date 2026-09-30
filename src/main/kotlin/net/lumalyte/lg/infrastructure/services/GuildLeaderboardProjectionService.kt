package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.domain.entities.EntityType
import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.LeaderboardEntry
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class GuildLeaderboardProjectionKey(
    val type: ExtendedLeaderboardType,
    val period: LeaderboardPeriod,
)

data class GuildLeaderboardProjectionResult(
    val changedBoards: Set<GuildLeaderboardProjectionKey>,
    val rowsWritten: Int,
    val rowsDeleted: Int,
)

class GuildLeaderboardProjectionService(
    private val plugin: LumaGuilds,
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val claimRepository: ClaimRepository?,
    private val goldRepository: GuildGoldRepository,
    private val killRepository: KillRepository,
    private val leaderboardRepository: LeaderboardRepository,
) {
    private val logger = LoggerFactory.getLogger(GuildLeaderboardProjectionService::class.java)
    private val running = AtomicBoolean(false)
    private var task: BukkitTask? = null

    fun start(onChanged: (Set<GuildLeaderboardProjectionKey>) -> Unit = {}) {
        if (task != null) return
        task = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable {
                if (!running.compareAndSet(false, true)) return@Runnable
                val snapshot = runCatching { captureSnapshot() }.getOrElse { error ->
                    running.set(false)
                    logger.error("Failed to capture guild leaderboard projection snapshot", error)
                    return@Runnable
                }
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    try {
                        val result = reconcile(snapshot)
                        if (result.changedBoards.isNotEmpty() && plugin.isEnabled) {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                onChanged(result.changedBoards)
                            })
                        }
                    } catch (error: Exception) {
                        logger.error("Failed to reconcile guild leaderboard projection", error)
                    } finally {
                        running.set(false)
                    }
                })
            },
            1L,
            REFRESH_TICKS,
        )
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    internal fun captureSnapshot(): Snapshot {
        val guilds = guildRepository.getAll().associateBy { it.id }
        val memberCounts = memberRepository.getAll()
            .groupingBy { it.guildId }
            .eachCount()
        val claimCounts = claimRepository?.getAll()
            ?.mapNotNull { it.teamId }
            ?.groupingBy { it }
            ?.eachCount()
            .orEmpty()
        return Snapshot(
            guildLevels = guilds.mapValues { (_, guild) -> guild.level.toDouble() },
            memberCounts = guilds.keys.associateWith { memberCounts[it]?.toDouble() ?: 0.0 },
            claimCounts = guilds.keys.associateWith { claimCounts[it]?.toDouble() ?: 0.0 },
        )
    }

    internal fun reconcile(snapshot: Snapshot): GuildLeaderboardProjectionResult {
        val guildIds = snapshot.guildLevels.keys
        val killStats = if (guildIds.isEmpty()) emptyMap() else killRepository.getAllGuildKillStats()

        val goldBalances = if (guildIds.isEmpty()) {
            emptyMap()
        } else {
            goldRepository.getTopBalances(Int.MAX_VALUE).toMap()
        }
        val weekStart = currentWeekStart()
        val weekly = if (guildIds.isEmpty()) {
            emptyMap()
        } else {
            leaderboardRepository.getWeeklyActivityForPeriod(weekStart, Int.MAX_VALUE)
                .associateBy { it.guildId }
        }

        val boards = listOf(
            Board(ExtendedLeaderboardType.GUILD_LEVEL, LeaderboardPeriod.ALL_TIME, snapshot.guildLevels),
            Board(
                ExtendedLeaderboardType.GUILD_KILLS,
                LeaderboardPeriod.ALL_TIME,
                guildIds.associateWith { killStats[it]?.totalKills?.toDouble() ?: 0.0 },
            ),
            Board(
                ExtendedLeaderboardType.GUILD_DEATHS,
                LeaderboardPeriod.ALL_TIME,
                guildIds.associateWith { killStats[it]?.totalDeaths?.toDouble() ?: 0.0 },
            ),
            Board(
                ExtendedLeaderboardType.GUILD_BANK_BALANCE,
                LeaderboardPeriod.ALL_TIME,
                guildIds.associateWith { goldBalances[it]?.toDouble() ?: 0.0 },
            ),
            Board(ExtendedLeaderboardType.GUILD_CLAIM_COUNT, LeaderboardPeriod.ALL_TIME, snapshot.claimCounts),
            Board(ExtendedLeaderboardType.GUILD_MEMBER_COUNT, LeaderboardPeriod.ALL_TIME, snapshot.memberCounts),

            Board(
                ExtendedLeaderboardType.WEEKLY_ACTIVITY,
                LeaderboardPeriod.WEEKLY,
                guildIds.associateWith { weekly[it]?.totalScore?.toDouble() ?: 0.0 },
                periodStart = weekStart,
                periodEnd = weekStart.plus(7, ChronoUnit.DAYS),
            ),
        )

        val changedBoards = linkedSetOf<GuildLeaderboardProjectionKey>()
        var rowsWritten = 0
        var rowsDeleted = 0
        boards.forEach { board ->
            val result = reconcileBoard(board)
            rowsWritten += result.rowsWritten
            rowsDeleted += result.rowsDeleted
            if (result.changed) {
                changedBoards += GuildLeaderboardProjectionKey(board.type, board.period)
            }
        }
        return GuildLeaderboardProjectionResult(changedBoards, rowsWritten, rowsDeleted)
    }

    private fun reconcileBoard(board: Board): BoardResult {
        val existingCount = leaderboardRepository.getLeaderboardEntryCount(board.type, board.period)
        val existing = leaderboardRepository.getLeaderboardEntries(
            board.type,
            board.period,
            existingCount.coerceAtLeast(1),
        )

        val grouped = existing
            .filter { it.entityType == EntityType.GUILD }
            .groupBy { it.entityId }
        val duplicateIds = grouped.filterValues { it.size > 1 }.keys
        val staleIds = grouped.keys - board.values.keys
        var rowsDeleted = 0
        (duplicateIds + staleIds).forEach { guildId ->
            rowsDeleted += leaderboardRepository.deleteLeaderboardEntries(board.type, guildId, board.period)
        }

        val canonical = grouped
            .filterKeys { it !in duplicateIds && it !in staleIds }
            .mapValues { (_, entries) -> entries.single() }
        val now = Instant.now()
        val desiredEntries = board.values.map { (guildId, value) ->
            val old = canonical[guildId]
            old?.copy(
                value = value,
                periodStart = board.periodStart,
                periodEnd = board.periodEnd,
                lastUpdated = if (old.value == value) old.lastUpdated else now,
            ) ?: LeaderboardEntry(
                leaderboardType = board.type,
                entityId = guildId,
                entityType = EntityType.GUILD,
                value = value,
                rank = 0,
                period = board.period,
                periodStart = board.periodStart,
                periodEnd = board.periodEnd,
                lastUpdated = now,
            )
        }

        val ranked = desiredEntries
            .sortedWith(compareByDescending<LeaderboardEntry> { it.value }.thenBy { it.entityId.toString() })
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

        val writes = ranked.filter { desired ->
            val old = canonical[desired.entityId]
            old == null ||
                old.value != desired.value ||
                old.rank != desired.rank ||
                old.periodStart != desired.periodStart ||
                old.periodEnd != desired.periodEnd
        }
        val rowsWritten = leaderboardRepository.batchUpdateEntries(writes)
        return BoardResult(
            changed = rowsDeleted > 0 || rowsWritten > 0,
            rowsWritten = rowsWritten,
            rowsDeleted = rowsDeleted,
        )
    }

    private fun currentWeekStart(): Instant =
        ZonedDateTime.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()

    internal data class Snapshot(
        val guildLevels: Map<UUID, Double>,
        val memberCounts: Map<UUID, Double>,
        val claimCounts: Map<UUID, Double>,
    )

    private data class Board(
        val type: ExtendedLeaderboardType,
        val period: LeaderboardPeriod,
        val values: Map<UUID, Double>,
        val periodStart: Instant? = null,
        val periodEnd: Instant? = null,
    )

    private data class BoardResult(
        val changed: Boolean,
        val rowsWritten: Int,
        val rowsDeleted: Int,
    )

    companion object {
        internal const val REFRESH_TICKS = 20L * 30L
    }
}
