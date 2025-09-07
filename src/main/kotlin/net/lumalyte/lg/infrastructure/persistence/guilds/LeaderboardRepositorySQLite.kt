package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.entities.LeaderboardType
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class LeaderboardRepositorySQLite(private val storage: SQLiteStorage) : LeaderboardRepository {

    private val leaderboardEntries: MutableMap<String, LeaderboardEntry> = mutableMapOf()
    private val weeklyActivities: MutableMap<String, WeeklyActivity> = mutableMapOf()
    private val leaderboardSnapshots: MutableMap<UUID, LeaderboardSnapshot> = mutableMapOf()
    private val leaderboardConfigs: MutableMap<ExtendedLeaderboardType, LeaderboardConfig> = mutableMapOf()

    init {
        createLeaderboardTables()
        preload()
    }

    private fun createLeaderboardTables() {
        val entriesTableSql = """
            CREATE TABLE IF NOT EXISTS leaderboard_entries (
                id TEXT PRIMARY KEY,
                leaderboard_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                value REAL NOT NULL,
                rank INTEGER NOT NULL,
                period TEXT NOT NULL,
                period_start INTEGER,
                period_end INTEGER,
                last_updated INTEGER NOT NULL
            )
        """.trimIndent()

        val weeklyActivityTableSql = """
            CREATE TABLE IF NOT EXISTS weekly_activity (
                guild_id TEXT PRIMARY KEY,
                week_start INTEGER NOT NULL,
                week_end INTEGER NOT NULL,
                kills INTEGER NOT NULL DEFAULT 0,
                deaths INTEGER NOT NULL DEFAULT 0,
                claims_created INTEGER NOT NULL DEFAULT 0,
                claims_destroyed INTEGER NOT NULL DEFAULT 0,
                members_joined INTEGER NOT NULL DEFAULT 0,
                members_left INTEGER NOT NULL DEFAULT 0,
                bank_deposits INTEGER NOT NULL DEFAULT 0,
                bank_withdrawals INTEGER NOT NULL DEFAULT 0,
                chat_messages INTEGER NOT NULL DEFAULT 0,
                parties_formed INTEGER NOT NULL DEFAULT 0,
                last_updated INTEGER NOT NULL
            )
        """.trimIndent()

        val snapshotsTableSql = """
            CREATE TABLE IF NOT EXISTS leaderboard_snapshots (
                id TEXT PRIMARY KEY,
                leaderboard_type TEXT NOT NULL,
                period TEXT NOT NULL,
                snapshot_time INTEGER NOT NULL,
                entries_data TEXT NOT NULL,
                metadata TEXT
            )
        """.trimIndent()

        val configsTableSql = """
            CREATE TABLE IF NOT EXISTS leaderboard_configs (
                leaderboard_type TEXT PRIMARY KEY,
                enabled INTEGER NOT NULL DEFAULT 1,
                max_entries INTEGER NOT NULL DEFAULT 100,
                update_interval_minutes INTEGER NOT NULL DEFAULT 60,
                reset_schedule TEXT NOT NULL
            )
        """.trimIndent()

        // Create indices for performance
        val entriesIndexSql = "CREATE INDEX IF NOT EXISTS idx_leaderboard_entries_type_period ON leaderboard_entries(leaderboard_type, period)"
        val entriesRankIndexSql = "CREATE INDEX IF NOT EXISTS idx_leaderboard_entries_rank ON leaderboard_entries(leaderboard_type, period, rank)"
        val weeklyActivityIndexSql = "CREATE INDEX IF NOT EXISTS idx_weekly_activity_week ON weekly_activity(week_start, week_end)"
        val snapshotsIndexSql = "CREATE INDEX IF NOT EXISTS idx_leaderboard_snapshots_type ON leaderboard_snapshots(leaderboard_type, period)"

        try {
            storage.connection.executeUpdate(entriesTableSql)
            storage.connection.executeUpdate(weeklyActivityTableSql)
            storage.connection.executeUpdate(snapshotsTableSql)
            storage.connection.executeUpdate(configsTableSql)
            storage.connection.executeUpdate(entriesIndexSql)
            storage.connection.executeUpdate(entriesRankIndexSql)
            storage.connection.executeUpdate(weeklyActivityIndexSql)
            storage.connection.executeUpdate(snapshotsIndexSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create leaderboard tables", e)
        }
    }

    private fun preload() {
        preloadLeaderboardEntries()
        preloadWeeklyActivities()
        preloadLeaderboardSnapshots()
        preloadLeaderboardConfigs()
    }

    private fun preloadLeaderboardEntries() {
        val sql = """
            SELECT id, leaderboard_type, entity_id, entity_type, value, rank, period,
                   period_start, period_end, last_updated
            FROM leaderboard_entries
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val entry = mapResultSetToLeaderboardEntry(result)
                val key = "${entry.leaderboardType}_${entry.entityId}_${entry.period}"
                leaderboardEntries[key] = entry
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload leaderboard entries", e)
        }
    }

    private fun preloadWeeklyActivities() {
        val sql = """
            SELECT guild_id, week_start, week_end, kills, deaths, claims_created,
                   claims_destroyed, members_joined, members_left, bank_deposits,
                   bank_withdrawals, chat_messages, parties_formed, last_updated
            FROM weekly_activity
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val activity = mapResultSetToWeeklyActivity(result)
                val key = "${activity.guildId}_${activity.weekStart}"
                weeklyActivities[key] = activity
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload weekly activities", e)
        }
    }

    private fun preloadLeaderboardSnapshots() {
        val sql = """
            SELECT id, leaderboard_type, period, snapshot_time, entries_data, metadata
            FROM leaderboard_snapshots
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val snapshot = mapResultSetToLeaderboardSnapshot(result)
                leaderboardSnapshots[snapshot.id] = snapshot
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload leaderboard snapshots", e)
        }
    }

    private fun preloadLeaderboardConfigs() {
        val sql = """
            SELECT leaderboard_type, enabled, max_entries, update_interval_minutes, reset_schedule
            FROM leaderboard_configs
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val config = mapResultSetToLeaderboardConfig(result)
                leaderboardConfigs[config.type] = config
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload leaderboard configs", e)
        }
    }

    private fun mapResultSetToLeaderboardEntry(rs: co.aikar.idb.DbRow): LeaderboardEntry {
        return LeaderboardEntry(
            id = UUID.fromString(rs.getString("id")),
            leaderboardType = ExtendedLeaderboardType.valueOf(rs.getString("leaderboard_type")),
            entityId = UUID.fromString(rs.getString("entity_id")),
            entityType = EntityType.valueOf(rs.getString("entity_type")),
            value = rs.getString("value")?.toDoubleOrNull() ?: 0.0,
            rank = rs.getInt("rank"),
            period = LeaderboardPeriod.valueOf(rs.getString("period")),
            periodStart = rs.getLong("period_start")?.let { Instant.ofEpochMilli(it) },
            periodEnd = rs.getLong("period_end")?.let { Instant.ofEpochMilli(it) },
            lastUpdated = Instant.ofEpochMilli(rs.getLong("last_updated"))
        )
    }

    private fun mapResultSetToWeeklyActivity(rs: co.aikar.idb.DbRow): WeeklyActivity {
        return WeeklyActivity(
            guildId = UUID.fromString(rs.getString("guild_id")),
            weekStart = Instant.ofEpochMilli(rs.getLong("week_start")),
            weekEnd = Instant.ofEpochMilli(rs.getLong("week_end")),
            kills = rs.getInt("kills"),
            deaths = rs.getInt("deaths"),
            claimsCreated = rs.getInt("claims_created"),
            claimsDestroyed = rs.getInt("claims_destroyed"),
            membersJoined = rs.getInt("members_joined"),
            membersLeft = rs.getInt("members_left"),
            bankDeposits = rs.getInt("bank_deposits"),
            bankWithdrawals = rs.getInt("bank_withdrawals"),
            chatMessages = rs.getInt("chat_messages"),
            partiesFormed = rs.getInt("parties_formed"),
            lastUpdated = Instant.ofEpochMilli(rs.getLong("last_updated"))
        )
    }

    private fun mapResultSetToLeaderboardSnapshot(rs: co.aikar.idb.DbRow): LeaderboardSnapshot {
        // For simplicity, we'll store entries as JSON in the database
        // In a real implementation, you'd parse this back to a list of LeaderboardEntry
        return LeaderboardSnapshot(
            id = UUID.fromString(rs.getString("id")),
            type = LeaderboardType.valueOf(rs.getString("leaderboard_type")),
            periodStart = Instant.ofEpochMilli(rs.getLong("snapshot_time")), // Use snapshot time as period start
            periodEnd = Instant.ofEpochMilli(rs.getLong("snapshot_time")), // Use snapshot time as period end for simplicity
            data = rs.getString("entries_data"),
            createdAt = Instant.ofEpochMilli(rs.getLong("snapshot_time"))
        )
    }

    private fun mapResultSetToLeaderboardConfig(rs: co.aikar.idb.DbRow): LeaderboardConfig {
        return LeaderboardConfig(
            type = ExtendedLeaderboardType.valueOf(rs.getString("leaderboard_type")),
            enabled = rs.getInt("enabled") == 1,
            maxEntries = rs.getInt("max_entries"),
            updateIntervalMinutes = rs.getInt("update_interval_minutes"),
            resetSchedule = ResetSchedule.valueOf(rs.getString("reset_schedule"))
        )
    }

    override fun saveLeaderboardEntry(entry: LeaderboardEntry): Boolean {
        val sql = """
            INSERT OR REPLACE INTO leaderboard_entries
            (id, leaderboard_type, entity_id, entity_type, value, rank, period, period_start, period_end, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                entry.id.toString(),
                entry.leaderboardType.name,
                entry.entityId.toString(),
                entry.entityType.name,
                entry.value,
                entry.rank,
                entry.period.name,
                entry.periodStart?.toEpochMilli(),
                entry.periodEnd?.toEpochMilli(),
                entry.lastUpdated.toEpochMilli()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to save leaderboard entry", e)
        }
    }

    override fun getLeaderboardEntries(type: ExtendedLeaderboardType, period: LeaderboardPeriod, limit: Int): List<LeaderboardEntry> {
        val sql = """
            SELECT id, leaderboard_type, entity_id, entity_type, value, rank, period,
                   period_start, period_end, last_updated
            FROM leaderboard_entries
            WHERE leaderboard_type = ? AND period = ?
            ORDER BY rank ASC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, type.name, period.name, limit)
            results.map { mapResultSetToLeaderboardEntry(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get leaderboard entries", e)
        }
    }

    override fun getLeaderboardEntry(type: ExtendedLeaderboardType, entityId: UUID, period: LeaderboardPeriod): LeaderboardEntry? {
        val sql = """
            SELECT id, leaderboard_type, entity_id, entity_type, value, rank, period,
                   period_start, period_end, last_updated
            FROM leaderboard_entries
            WHERE leaderboard_type = ? AND entity_id = ? AND period = ?
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, type.name, entityId.toString(), period.name)
            result?.let { mapResultSetToLeaderboardEntry(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get leaderboard entry", e)
        }
    }

    override fun getLeaderboardEntriesPaged(type: ExtendedLeaderboardType, period: LeaderboardPeriod, offset: Int, limit: Int): List<LeaderboardEntry> {
        val sql = """
            SELECT id, leaderboard_type, entity_id, entity_type, value, rank, period,
                   period_start, period_end, last_updated
            FROM leaderboard_entries
            WHERE leaderboard_type = ? AND period = ?
            ORDER BY rank ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, type.name, period.name, limit, offset)
            results.map { mapResultSetToLeaderboardEntry(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get paginated leaderboard entries", e)
        }
    }

    override fun getEntityRank(type: ExtendedLeaderboardType, entityId: UUID, period: LeaderboardPeriod): Int? {
        val entry = getLeaderboardEntry(type, entityId, period)
        return entry?.rank
    }

    override fun batchUpdateEntries(entries: List<LeaderboardEntry>): Int {
        var updatedCount = 0
        for (entry in entries) {
            if (saveLeaderboardEntry(entry)) {
                updatedCount++
            }
        }
        return updatedCount
    }

    override fun saveLeaderboardSnapshot(snapshot: LeaderboardSnapshot): Boolean {
        val sql = """
            INSERT OR REPLACE INTO leaderboard_snapshots
            (id, leaderboard_type, period, snapshot_time, entries_data, metadata)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            // For simplicity, we'll just store placeholder data
            // In a real implementation, you'd serialize the entries and metadata
            storage.connection.executeUpdate(sql,
                snapshot.id.toString(),
                snapshot.type.name,
                LeaderboardPeriod.ALL_TIME.name, // Use ALL_TIME as default period
                snapshot.createdAt.toEpochMilli(),
                snapshot.data,
                "{}"  // Placeholder for metadata JSON
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to save leaderboard snapshot", e)
        }
    }

    override fun getLeaderboardSnapshots(type: ExtendedLeaderboardType, period: LeaderboardPeriod, limit: Int): List<LeaderboardSnapshot> {
        val sql = """
            SELECT id, leaderboard_type, period, snapshot_time, entries_data, metadata
            FROM leaderboard_snapshots
            WHERE leaderboard_type = ? AND period = ?
            ORDER BY snapshot_time DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, LeaderboardType.KILLS.name, period.name, limit)
            results.map { mapResultSetToLeaderboardSnapshot(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get leaderboard snapshots", e)
        }
    }

    override fun saveWeeklyActivity(activity: WeeklyActivity): Boolean {
        val sql = """
            INSERT OR REPLACE INTO weekly_activity
            (guild_id, week_start, week_end, kills, deaths, claims_created, claims_destroyed,
             members_joined, members_left, bank_deposits, bank_withdrawals, chat_messages,
             parties_formed, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                activity.guildId.toString(),
                activity.weekStart.toEpochMilli(),
                activity.weekEnd.toEpochMilli(),
                activity.kills,
                activity.deaths,
                activity.claimsCreated,
                activity.claimsDestroyed,
                activity.membersJoined,
                activity.membersLeft,
                activity.bankDeposits,
                activity.bankWithdrawals,
                activity.chatMessages,
                activity.partiesFormed,
                activity.lastUpdated.toEpochMilli()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to save weekly activity", e)
        }
    }

    override fun getWeeklyActivity(guildId: UUID, weekStart: Instant): WeeklyActivity? {
        val sql = """
            SELECT guild_id, week_start, week_end, kills, deaths, claims_created,
                   claims_destroyed, members_joined, members_left, bank_deposits,
                   bank_withdrawals, chat_messages, parties_formed, last_updated
            FROM weekly_activity
            WHERE guild_id = ? AND week_start = ?
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString(), weekStart.toEpochMilli())
            result?.let { mapResultSetToWeeklyActivity(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get weekly activity", e)
        }
    }

    override fun getWeeklyActivityForPeriod(weekStart: Instant, limit: Int): List<WeeklyActivity> {
        val sql = """
            SELECT guild_id, week_start, week_end, kills, deaths, claims_created,
                   claims_destroyed, members_joined, members_left, bank_deposits,
                   bank_withdrawals, chat_messages, parties_formed, last_updated
            FROM weekly_activity
            WHERE week_start = ?
            ORDER BY last_updated DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, weekStart.toEpochMilli(), limit)
            results.map { mapResultSetToWeeklyActivity(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get weekly activity for period", e)
        }
    }

    override fun saveLeaderboardConfig(config: LeaderboardConfig): Boolean {
        val sql = """
            INSERT OR REPLACE INTO leaderboard_configs
            (leaderboard_type, enabled, max_entries, update_interval_minutes, reset_schedule)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                config.type.name,
                if (config.enabled) 1 else 0,
                config.maxEntries,
                config.updateIntervalMinutes,
                config.resetSchedule.name
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to save leaderboard config", e)
        }
    }

    override fun getLeaderboardConfig(type: ExtendedLeaderboardType): LeaderboardConfig? {
        val sql = """
            SELECT leaderboard_type, enabled, max_entries, update_interval_minutes, reset_schedule
            FROM leaderboard_configs
            WHERE leaderboard_type = ?
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql, type.name)
            result?.let { mapResultSetToLeaderboardConfig(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get leaderboard config", e)
        }
    }

    override fun deleteOldEntries(maxAgeDays: Int): Int {
        val cutoffTime = Instant.now().minusSeconds(maxAgeDays * 24 * 60 * 60L)
        val sql = "DELETE FROM leaderboard_entries WHERE last_updated < ?"

        return try {
            storage.connection.executeUpdate(sql, cutoffTime.toEpochMilli())
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to delete old leaderboard entries", e)
        }
    }

    override fun resetLeaderboardPeriod(type: ExtendedLeaderboardType, period: LeaderboardPeriod, newPeriodStart: Instant): Boolean {
        val sql = "DELETE FROM leaderboard_entries WHERE leaderboard_type = ? AND period = ?"

        return try {
            storage.connection.executeUpdate(sql, type.name, period.name) >= 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to reset leaderboard period", e)
        }
    }

    override fun getLeaderboardEntryCount(type: ExtendedLeaderboardType, period: LeaderboardPeriod): Int {
        val sql = "SELECT COUNT(*) as count FROM leaderboard_entries WHERE leaderboard_type = ? AND period = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, type.name, period.name)
            result?.getInt("count") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get leaderboard entry count", e)
        }
    }

    override fun updateLastUpdated(type: ExtendedLeaderboardType, period: LeaderboardPeriod, entityIds: List<UUID>): Int {
        if (entityIds.isEmpty()) return 0

        val placeholders = entityIds.joinToString(",") { "?" }
        val sql = """
            UPDATE leaderboard_entries
            SET last_updated = ?
            WHERE leaderboard_type = ? AND period = ? AND entity_id IN ($placeholders)
        """.trimIndent()

        return try {
            val params = mutableListOf<Any>()
            params.add(Instant.now().toEpochMilli())
            params.add(type.name)
            params.add(period.name)
            params.addAll(entityIds.map { it.toString() })

            storage.connection.executeUpdate(sql, *params.toTypedArray())
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update last updated timestamps", e)
        }
    }
}
