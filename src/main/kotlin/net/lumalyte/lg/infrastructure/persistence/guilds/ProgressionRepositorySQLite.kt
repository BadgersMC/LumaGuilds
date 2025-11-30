package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.ActivityMetricType
import net.lumalyte.lg.application.persistence.ProgressionStats
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.application.services.PerkType
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class ProgressionRepositorySQLite(private val storage: Storage<Database>) : ProgressionRepository {

    private val guildProgressions: MutableMap<UUID, GuildProgression> = mutableMapOf()
    private val activityMetrics: MutableMap<UUID, GuildActivityMetrics> = mutableMapOf()

    init {
        // Try to create tables and preload, but don't fail if migration hasn't run yet
        try {
            createProgressionTables()
            preload()
        } catch (e: SQLException) {
            // Tables don't exist yet - migration will create them later
            // This is expected on first startup before migration runs
        }
    }

    private fun createProgressionTables() {
        val progressionTableSql = """
            CREATE TABLE IF NOT EXISTS guild_progression (
                guild_id TEXT PRIMARY KEY,
                total_experience INTEGER NOT NULL DEFAULT 0,
                current_level INTEGER NOT NULL DEFAULT 1,
                experience_this_level INTEGER NOT NULL DEFAULT 0,
                experience_for_next_level INTEGER NOT NULL DEFAULT 1000,
                last_level_up INTEGER,
                total_level_ups INTEGER NOT NULL DEFAULT 0,
                unlocked_perks TEXT NOT NULL, -- JSON array of perk names
                created_at INTEGER NOT NULL,
                last_updated INTEGER NOT NULL
            )
        """.trimIndent()

        val experienceTransactionsTableSql = """
            CREATE TABLE IF NOT EXISTS experience_transactions (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                amount INTEGER NOT NULL,
                source TEXT NOT NULL,
                description TEXT,
                actor_id TEXT,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent()

        val activityMetricsTableSql = """
            CREATE TABLE IF NOT EXISTS guild_activity_metrics (
                guild_id TEXT PRIMARY KEY,
                member_count INTEGER NOT NULL DEFAULT 0,
                active_members INTEGER NOT NULL DEFAULT 0,
                claims_owned INTEGER NOT NULL DEFAULT 0,
                claims_created_this_week INTEGER NOT NULL DEFAULT 0,
                kills_this_week INTEGER NOT NULL DEFAULT 0,
                deaths_this_week INTEGER NOT NULL DEFAULT 0,
                bank_deposits_this_week INTEGER NOT NULL DEFAULT 0,
                relations_formed INTEGER NOT NULL DEFAULT 0,
                wars_participated INTEGER NOT NULL DEFAULT 0,
                last_calculated INTEGER NOT NULL
            )
        """.trimIndent()

        // Create indices for performance
        val progressionIndexSql = "CREATE INDEX IF NOT EXISTS idx_guild_progression_level ON guild_progression(current_level)"
        val transactionsIndexSql = "CREATE INDEX IF NOT EXISTS idx_experience_transactions_guild ON experience_transactions(guild_id)"
        val transactionsTimeIndexSql = "CREATE INDEX IF NOT EXISTS idx_experience_transactions_time ON experience_transactions(timestamp)"
        val activityIndexSql = "CREATE INDEX IF NOT EXISTS idx_guild_activity_metrics_active ON guild_activity_metrics(active_members)"

        try {
            storage.connection.executeUpdate(progressionTableSql)
            storage.connection.executeUpdate(experienceTransactionsTableSql)
            storage.connection.executeUpdate(activityMetricsTableSql)
            storage.connection.executeUpdate(progressionIndexSql)
            storage.connection.executeUpdate(transactionsIndexSql)
            storage.connection.executeUpdate(transactionsTimeIndexSql)
            storage.connection.executeUpdate(activityIndexSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create progression tables", e)
        }
    }

    private fun preload() {
        preloadGuildProgressions()
        preloadActivityMetrics()
    }

    /**
     * Ensures tables exist and are properly initialized.
     * Called before any database operation.
     */
    private fun ensureTablesExist() {
        try {
            createProgressionTables()
            // Try a simple query to verify tables exist
            val sql = "SELECT COUNT(*) FROM guild_progression LIMIT 1"
            storage.connection.getResults(sql)
            // Tables exist and are accessible
        } catch (e: SQLException) {
            // Tables might not exist yet - create them
            try {
                createProgressionTables()
            } catch (e2: SQLException) {
                // Still can't create tables - this is expected if migration hasn't run
            }
        }
    }

    private fun preloadGuildProgressions() {
        val sql = """
            SELECT guild_id, total_experience, current_level, experience_this_level,
                   experience_for_next_level, last_level_up, total_level_ups,
                   unlocked_perks, created_at, last_updated
            FROM guild_progression
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val progression = mapResultSetToGuildProgression(result)
                guildProgressions[progression.guildId] = progression
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload guild progressions", e)
        }
    }

    private fun preloadActivityMetrics() {
        val sql = """
            SELECT guild_id, member_count, active_members, claims_owned,
                   claims_created_this_week, kills_this_week, deaths_this_week,
                   bank_deposits_this_week, relations_formed, wars_participated,
                   last_calculated
            FROM guild_activity_metrics
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val metrics = mapResultSetToActivityMetrics(result)
                activityMetrics[metrics.guildId] = metrics
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload activity metrics", e)
        }
    }

    private fun mapResultSetToGuildProgression(rs: co.aikar.idb.DbRow): GuildProgression {
        val unlockedPerksJson = rs.getString("unlocked_perks")
        val unlockedPerks = parseUnlockedPerks(unlockedPerksJson)

        return GuildProgression(
            guildId = UUID.fromString(rs.getString("guild_id")),
            totalExperience = rs.getInt("total_experience"),
            currentLevel = rs.getInt("current_level"),
            experienceThisLevel = rs.getInt("experience_this_level"),
            experienceForNextLevel = rs.getInt("experience_for_next_level"),
            lastLevelUp = rs.getLong("last_level_up")?.let { Instant.ofEpochMilli(it) },
            totalLevelUps = rs.getInt("total_level_ups"),
            unlockedPerks = unlockedPerks,
            createdAt = Instant.ofEpochMilli(rs.getLong("created_at")),
            lastUpdated = Instant.ofEpochMilli(rs.getLong("last_updated"))
        )
    }

    private fun mapResultSetToActivityMetrics(rs: co.aikar.idb.DbRow): GuildActivityMetrics {
        return GuildActivityMetrics(
            guildId = UUID.fromString(rs.getString("guild_id")),
            memberCount = rs.getInt("member_count"),
            activeMembers = rs.getInt("active_members"),
            claimsOwned = rs.getInt("claims_owned"),
            claimsCreatedThisWeek = rs.getInt("claims_created_this_week"),
            killsThisWeek = rs.getInt("kills_this_week"),
            deathsThisWeek = rs.getInt("deaths_this_week"),
            bankDepositsThisWeek = rs.getInt("bank_deposits_this_week"),
            relationsFormed = rs.getInt("relations_formed"),
            warsParticipated = rs.getInt("wars_participated"),
            lastCalculated = Instant.ofEpochMilli(rs.getLong("last_calculated"))
        )
    }

    private fun parseUnlockedPerks(json: String): Set<net.lumalyte.lg.application.services.PerkType> {
        if (json.isBlank() || json == "[]") return emptySet()

        return try {
            json.trim('[', ']')
                .split(',')
                .map { it.trim().trim('"') }
                .filter { it.isNotEmpty() }
                .map { net.lumalyte.lg.application.services.PerkType.valueOf(it) }
                .toSet()
        } catch (e: SQLException) {
            emptySet()
        }
    }

    private fun serializeUnlockedPerks(perks: Set<net.lumalyte.lg.application.services.PerkType>): String {
        if (perks.isEmpty()) return "[]"

        return perks.joinToString(",", "[", "]") { "\"$it\"" }
    }

    override fun saveGuildProgression(progression: GuildProgression): Boolean {
        ensureTablesExist()
        
        val sql = """
            INSERT OR REPLACE INTO guild_progression
            (guild_id, total_experience, current_level, experience_this_level,
             experience_for_next_level, last_level_up, total_level_ups,
             unlocked_perks, created_at, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val success = storage.connection.executeUpdate(sql,
                progression.guildId.toString(),
                progression.totalExperience,
                progression.currentLevel,
                progression.experienceThisLevel,
                progression.experienceForNextLevel,
                progression.lastLevelUp?.toEpochMilli(),
                progression.totalLevelUps,
                serializeUnlockedPerks(progression.unlockedPerks),
                progression.createdAt.toEpochMilli(),
                progression.lastUpdated.toEpochMilli()
            ) > 0
            
            // Update the in-memory cache if database save was successful
            if (success) {
                guildProgressions[progression.guildId] = progression
            }
            
            success
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to save guild progression", e)
        }
    }

    override fun getGuildProgression(guildId: UUID): GuildProgression? {
        ensureTablesExist()
        return guildProgressions[guildId] ?: createDefaultProgressionIfNotExists(guildId)
    }

    private fun createDefaultProgressionIfNotExists(guildId: UUID): GuildProgression? {
        // Check if it exists in the database
        val sql = "SELECT COUNT(*) as count FROM guild_progression WHERE guild_id = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            val exists = result?.getInt("count") ?: 0 > 0

            if (!exists) {
                // Create default progression
                val defaultProgression = GuildProgression.create(guildId)
                if (saveGuildProgression(defaultProgression)) {
                    guildProgressions[guildId] = defaultProgression
                    return defaultProgression
                }
            }
            null
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to check guild progression existence", e)
        }
    }

    override fun recordExperienceTransaction(transaction: ExperienceTransaction): Boolean {
        val sql = """
            INSERT INTO experience_transactions
            (id, guild_id, amount, source, description, actor_id, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                transaction.id.toString(),
                transaction.guildId.toString(),
                transaction.amount,
                transaction.source.name,
                transaction.description,
                transaction.actorId?.toString(),
                transaction.timestamp.toEpochMilli()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to record experience transaction", e)
        }
    }

    override fun getExperienceTransactions(guildId: UUID, limit: Int): List<ExperienceTransaction> {
        val sql = """
            SELECT id, guild_id, amount, source, description, actor_id, timestamp
            FROM experience_transactions
            WHERE guild_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, guildId.toString(), limit)
            results.map { rs ->
                ExperienceTransaction(
                    id = UUID.fromString(rs.getString("id")),
                    guildId = UUID.fromString(rs.getString("guild_id")),
                    amount = rs.getInt("amount"),
                    source = ExperienceSource.valueOf(rs.getString("source")),
                    description = rs.getString("description"),
                    actorId = rs.getString("actor_id")?.let { UUID.fromString(it) },
                    timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"))
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get experience transactions", e)
        }
    }

    override fun getExperienceFromSource(guildId: UUID, source: ExperienceSource): Int {
        val sql = "SELECT SUM(amount) as total FROM experience_transactions WHERE guild_id = ? AND source = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString(), source.name)
            result?.getInt("total") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get experience from source", e)
        }
    }

    override fun saveActivityMetrics(metrics: GuildActivityMetrics): Boolean {
        val sql = """
            INSERT OR REPLACE INTO guild_activity_metrics
            (guild_id, member_count, active_members, claims_owned, claims_created_this_week,
             kills_this_week, deaths_this_week, bank_deposits_this_week, relations_formed,
             wars_participated, last_calculated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                metrics.guildId.toString(),
                metrics.memberCount,
                metrics.activeMembers,
                metrics.claimsOwned,
                metrics.claimsCreatedThisWeek,
                metrics.killsThisWeek,
                metrics.deathsThisWeek,
                metrics.bankDepositsThisWeek,
                metrics.relationsFormed,
                metrics.warsParticipated,
                metrics.lastCalculated.toEpochMilli()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to save activity metrics", e)
        }
    }

    override fun getActivityMetrics(guildId: UUID): GuildActivityMetrics? {
        return activityMetrics[guildId] ?: createDefaultActivityMetricsIfNotExists(guildId)
    }

    private fun createDefaultActivityMetricsIfNotExists(guildId: UUID): GuildActivityMetrics? {
        // Check if it exists in the database
        val sql = "SELECT COUNT(*) as count FROM guild_activity_metrics WHERE guild_id = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            val exists = result?.getInt("count") ?: 0 > 0

            if (!exists) {
                // Create default metrics
                val defaultMetrics = GuildActivityMetrics(guildId = guildId)
                if (saveActivityMetrics(defaultMetrics)) {
                    activityMetrics[guildId] = defaultMetrics
                    return defaultMetrics
                }
            }
            null
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to check activity metrics existence", e)
        }
    }

    override fun getAllActivityMetrics(limit: Int): List<GuildActivityMetrics> {
        val sql = """
            SELECT guild_id, member_count, active_members, claims_owned,
                   claims_created_this_week, kills_this_week, deaths_this_week,
                   bank_deposits_this_week, relations_formed, wars_participated,
                   last_calculated
            FROM guild_activity_metrics
            ORDER BY last_calculated DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, limit)
            results.map { mapResultSetToActivityMetrics(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get all activity metrics", e)
        }
    }

    override fun updateActivityMetric(guildId: UUID, metricType: net.lumalyte.lg.application.persistence.ActivityMetricType, value: Int): Boolean {
        val columnName = when (metricType) {
            net.lumalyte.lg.application.persistence.ActivityMetricType.MEMBER_COUNT -> "member_count"
            net.lumalyte.lg.application.persistence.ActivityMetricType.ACTIVE_MEMBERS -> "active_members"
            net.lumalyte.lg.application.persistence.ActivityMetricType.CLAIMS_OWNED -> "claims_owned"
            net.lumalyte.lg.application.persistence.ActivityMetricType.CLAIMS_CREATED_THIS_WEEK -> "claims_created_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.KILLS_THIS_WEEK -> "kills_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.DEATHS_THIS_WEEK -> "deaths_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.BANK_DEPOSITS_THIS_WEEK -> "bank_deposits_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.RELATIONS_FORMED -> "relations_formed"
            net.lumalyte.lg.application.persistence.ActivityMetricType.WARS_PARTICIPATED -> "wars_participated"
        }

        val sql = "UPDATE guild_activity_metrics SET $columnName = ?, last_calculated = ? WHERE guild_id = ?"

        return try {
            storage.connection.executeUpdate(sql,
                value,
                Instant.now().toEpochMilli(),
                guildId.toString()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update activity metric", e)
        }
    }

    override fun incrementActivityMetric(guildId: UUID, metricType: net.lumalyte.lg.application.persistence.ActivityMetricType, amount: Int): Boolean {
        val columnName = when (metricType) {
            net.lumalyte.lg.application.persistence.ActivityMetricType.MEMBER_COUNT -> "member_count"
            net.lumalyte.lg.application.persistence.ActivityMetricType.ACTIVE_MEMBERS -> "active_members"
            net.lumalyte.lg.application.persistence.ActivityMetricType.CLAIMS_OWNED -> "claims_owned"
            net.lumalyte.lg.application.persistence.ActivityMetricType.CLAIMS_CREATED_THIS_WEEK -> "claims_created_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.KILLS_THIS_WEEK -> "kills_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.DEATHS_THIS_WEEK -> "deaths_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.BANK_DEPOSITS_THIS_WEEK -> "bank_deposits_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.RELATIONS_FORMED -> "relations_formed"
            net.lumalyte.lg.application.persistence.ActivityMetricType.WARS_PARTICIPATED -> "wars_participated"
        }

        val sql = "UPDATE guild_activity_metrics SET $columnName = $columnName + ?, last_calculated = ? WHERE guild_id = ?"

        return try {
            storage.connection.executeUpdate(sql,
                amount,
                Instant.now().toEpochMilli(),
                guildId.toString()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to increment activity metric", e)
        }
    }

    override fun getTopGuildsByMetric(metricType: net.lumalyte.lg.application.persistence.ActivityMetricType, limit: Int): List<Pair<UUID, Int>> {
        val columnName = when (metricType) {
            net.lumalyte.lg.application.persistence.ActivityMetricType.MEMBER_COUNT -> "member_count"
            net.lumalyte.lg.application.persistence.ActivityMetricType.ACTIVE_MEMBERS -> "active_members"
            net.lumalyte.lg.application.persistence.ActivityMetricType.CLAIMS_OWNED -> "claims_owned"
            net.lumalyte.lg.application.persistence.ActivityMetricType.CLAIMS_CREATED_THIS_WEEK -> "claims_created_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.KILLS_THIS_WEEK -> "kills_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.DEATHS_THIS_WEEK -> "deaths_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.BANK_DEPOSITS_THIS_WEEK -> "bank_deposits_this_week"
            net.lumalyte.lg.application.persistence.ActivityMetricType.RELATIONS_FORMED -> "relations_formed"
            net.lumalyte.lg.application.persistence.ActivityMetricType.WARS_PARTICIPATED -> "wars_participated"
        }

        val sql = """
            SELECT guild_id, $columnName as value
            FROM guild_activity_metrics
            ORDER BY $columnName DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, limit)
            results.map { rs ->
                Pair(UUID.fromString(rs.getString("guild_id")), rs.getInt("value"))
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get top guilds by metric", e)
        }
    }

    override fun resetAllActivityMetrics(): Int {
        val sql = """
            UPDATE guild_activity_metrics SET
            claims_created_this_week = 0,
            kills_this_week = 0,
            deaths_this_week = 0,
            bank_deposits_this_week = 0,
            relations_formed = 0,
            wars_participated = 0,
            last_calculated = ?
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql, Instant.now().toEpochMilli())
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to reset all activity metrics", e)
        }
    }

    override fun getProgressionStats(): net.lumalyte.lg.application.persistence.ProgressionStats {
        val sql = """
            SELECT
                COUNT(*) as total_guilds,
                AVG(current_level) as average_level,
                MAX(current_level) as highest_level,
                SUM(total_experience) as total_experience,
                SUM(total_level_ups) as total_level_ups
            FROM guild_progression
        """.trimIndent()

        return try {
            val result = storage.connection.getFirstRow(sql)
            val levelDistribution = getLevelDistribution()

            net.lumalyte.lg.application.persistence.ProgressionStats(
                totalGuilds = result?.getInt("total_guilds") ?: 0,
                averageLevel = result?.getString("average_level")?.toDoubleOrNull() ?: 1.0,
                highestLevel = result?.getInt("highest_level") ?: 1,
                totalExperienceAwarded = result?.getLong("total_experience") ?: 0,
                totalLevelUps = result?.getInt("total_level_ups") ?: 0,
                mostCommonLevel = levelDistribution.maxByOrNull { it.value }?.key ?: 1
            )
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get progression stats", e)
        }
    }

    override fun getLevelDistribution(): Map<Int, Int> {
        val sql = """
            SELECT current_level, COUNT(*) as count
            FROM guild_progression
            GROUP BY current_level
            ORDER BY current_level
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql)
            results.associate { rs ->
                rs.getInt("current_level") to rs.getInt("count")
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get level distribution", e)
        }
    }

    override fun deleteOldTransactions(maxAgeDays: Int): Int {
        val cutoffTime = Instant.now().minusSeconds(maxAgeDays * 24 * 60 * 60L)
        val sql = "DELETE FROM experience_transactions WHERE timestamp < ?"

        return try {
            storage.connection.executeUpdate(sql, cutoffTime.toEpochMilli())
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to delete old transactions", e)
        }
    }

    override fun getAverageGuildLevel(): Double {
        val sql = "SELECT AVG(current_level) as average FROM guild_progression"

        return try {
            val result = storage.connection.getFirstRow(sql)
            result?.getString("average")?.toDoubleOrNull() ?: 1.0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get average guild level", e)
        }
    }

    override fun getHighestGuildLevel(): Int {
        val sql = "SELECT MAX(current_level) as highest FROM guild_progression"

        return try {
            val result = storage.connection.getFirstRow(sql)
            result?.getInt("highest") ?: 1
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get highest guild level", e)
        }
    }

    override fun getRecentLevelUps(since: Instant, limit: Int): List<Pair<UUID, Int>> {
        val sql = """
            SELECT guild_id, current_level
            FROM guild_progression
            WHERE last_level_up >= ?
            ORDER BY last_level_up DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, since.toEpochMilli(), limit)
            results.map { rs ->
                Pair(UUID.fromString(rs.getString("guild_id")), rs.getInt("current_level"))
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get recent level ups", e)
        }
    }
}
