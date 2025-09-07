package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class KillRepositorySQLite(private val storage: SQLiteStorage) : KillRepository {

    private val kills: MutableMap<UUID, Kill> = mutableMapOf()
    private val guildStats: MutableMap<UUID, GuildKillStats> = mutableMapOf()
    private val playerStats: MutableMap<UUID, PlayerKillStats> = mutableMapOf()
    private val antiFarmData: MutableMap<UUID, AntiFarmData> = mutableMapOf()
    private var isInitialized = false

    init {
        // Defer table creation and preloading until first database access
        // This prevents issues when the database file doesn't exist yet
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            try {
                createKillTables()
                preload()
                isInitialized = true
            } catch (e: SQLException) {
                throw DatabaseOperationException("Failed to initialize kill database", e)
            }
        }
    }

    private fun createKillTables() {
        val killsTableSql = """
            CREATE TABLE IF NOT EXISTS kills (
                id TEXT PRIMARY KEY,
                killer_id TEXT NOT NULL,
                victim_id TEXT NOT NULL,
                killer_guild_id TEXT,
                victim_guild_id TEXT,
                timestamp TEXT NOT NULL,
                weapon TEXT,
                location_world TEXT,
                location_x REAL,
                location_y REAL,
                location_z REAL
            )
        """.trimIndent()

        val guildStatsTableSql = """
            CREATE TABLE IF NOT EXISTS guild_kill_stats (
                guild_id TEXT PRIMARY KEY,
                total_kills INTEGER NOT NULL DEFAULT 0,
                total_deaths INTEGER NOT NULL DEFAULT 0,
                net_kills INTEGER NOT NULL DEFAULT 0,
                kill_death_ratio REAL NOT NULL DEFAULT 0.0,
                last_updated TEXT NOT NULL
            )
        """.trimIndent()

        val playerStatsTableSql = """
            CREATE TABLE IF NOT EXISTS player_kill_stats (
                player_id TEXT PRIMARY KEY,
                guild_id TEXT,
                total_kills INTEGER NOT NULL DEFAULT 0,
                total_deaths INTEGER NOT NULL DEFAULT 0,
                streak INTEGER NOT NULL DEFAULT 0,
                best_streak INTEGER NOT NULL DEFAULT 0,
                last_kill_time INTEGER,
                last_death_time INTEGER
            )
        """.trimIndent()

        val antiFarmTableSql = """
            CREATE TABLE IF NOT EXISTS anti_farm_data (
                player_id TEXT PRIMARY KEY,
                recent_kills TEXT NOT NULL, -- JSON array of timestamps
                farm_score REAL NOT NULL DEFAULT 0.0,
                last_farm_check TEXT NOT NULL,
                is_currently_farming INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // Create indices for performance
        val killsIndexSql = "CREATE INDEX IF NOT EXISTS idx_kills_timestamp ON kills(timestamp)"
        val killsGuildIndexSql = "CREATE INDEX IF NOT EXISTS idx_kills_guilds ON kills(killer_guild_id, victim_guild_id)"
        val killsPlayerIndexSql = "CREATE INDEX IF NOT EXISTS idx_kills_players ON kills(killer_id, victim_id)"

        try {
            storage.connection.executeUpdate(killsTableSql)
            storage.connection.executeUpdate(guildStatsTableSql)
            storage.connection.executeUpdate(playerStatsTableSql)
            storage.connection.executeUpdate(antiFarmTableSql)
            storage.connection.executeUpdate(killsIndexSql)
            storage.connection.executeUpdate(killsGuildIndexSql)
            storage.connection.executeUpdate(killsPlayerIndexSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create kill tables", e)
        }
    }

    private fun preload() {
        preloadKills()
        preloadGuildStats()
        preloadPlayerStats()
        preloadAntiFarmData()
    }

    private fun preloadKills() {
        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            ORDER BY timestamp DESC
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val kill = mapResultSetToKill(result)
                kills[kill.id] = kill
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload kills", e)
        }
    }

    private fun preloadGuildStats() {
        val sql = """
            SELECT guild_id, total_kills, total_deaths, net_kills, kill_death_ratio, last_updated
            FROM guild_kill_stats
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val guildId = UUID.fromString(result.getString("guild_id"))
                val stats = GuildKillStats(
                    guildId = guildId,
                    totalKills = result.getInt("total_kills"),
                    totalDeaths = result.getInt("total_deaths"),
                    netKills = result.getInt("net_kills"),
                    killDeathRatio = result.getString("kill_death_ratio")?.toDouble() ?: 0.0,
                    lastUpdated = Instant.parse(result.getString("last_updated"))
                )
                guildStats[guildId] = stats
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload guild kill stats", e)
        }
    }

    private fun preloadPlayerStats() {
        val sql = """
            SELECT player_id, guild_id, total_kills, total_deaths, streak, best_streak,
                   last_kill_time, last_death_time
            FROM player_kill_stats
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val playerId = UUID.fromString(result.getString("player_id"))
                val guildId = result.getString("guild_id")?.let { UUID.fromString(it) }
                val stats = PlayerKillStats(
                    playerId = playerId,
                    guildId = guildId,
                    totalKills = result.getInt("total_kills"),
                    totalDeaths = result.getInt("total_deaths"),
                    streak = result.getInt("streak"),
                    bestStreak = result.getInt("best_streak"),
                    lastKillTime = result.getLong("last_kill_time")?.let { Instant.ofEpochMilli(it) },
                    lastDeathTime = result.getLong("last_death_time")?.let { Instant.ofEpochMilli(it) }
                )
                playerStats[playerId] = stats
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload player kill stats", e)
        }
    }

    private fun preloadAntiFarmData() {
        val sql = """
            SELECT player_id, recent_kills, farm_score, last_farm_check, is_currently_farming
            FROM anti_farm_data
        """.trimIndent()

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val playerId = UUID.fromString(result.getString("player_id"))
                val recentKillsJson = result.getString("recent_kills")
                val recentKills = parseRecentKills(recentKillsJson)
                val data = AntiFarmData(
                    playerId = playerId,
                    recentKills = recentKills,
                    farmScore = result.getString("farm_score")?.toDouble() ?: 0.0,
                    lastFarmCheck = Instant.parse(result.getString("last_farm_check")),
                    isCurrentlyFarming = result.getInt("is_currently_farming") == 1
                )
                antiFarmData[playerId] = data
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload anti-farm data", e)
        }
    }

    private fun mapResultSetToKill(rs: co.aikar.idb.DbRow): Kill {
        val worldId = rs.getString("location_world")?.let { UUID.fromString(it) }
        val location = if (worldId != null) {
            Position3D(
                x = rs.getInt("location_x"),
                y = rs.getInt("location_y"),
                z = rs.getInt("location_z")
            )
        } else null

        return Kill(
            id = UUID.fromString(rs.getString("id")),
            killerId = UUID.fromString(rs.getString("killer_id")),
            victimId = UUID.fromString(rs.getString("victim_id")),
            killerGuildId = rs.getString("killer_guild_id")?.let { UUID.fromString(it) },
            victimGuildId = rs.getString("victim_guild_id")?.let { UUID.fromString(it) },
            timestamp = Instant.parse(rs.getString("timestamp")),
            weapon = rs.getString("weapon"),
            worldId = worldId,
            location = location
        )
    }

    private fun parseRecentKills(json: String): List<Instant> {
        // Simple parsing - in a real implementation, you'd use a JSON library
        if (json.isBlank() || json == "[]") return emptyList()

        return try {
            json.trim('[', ']')
                .split(',')
                .map { it.trim().trim('"') }
                .filter { it.isNotEmpty() }
                .map { Instant.parse(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeRecentKills(kills: List<Instant>): String {
        // Simple serialization - in a real implementation, you'd use a JSON library
        if (kills.isEmpty()) return "[]"

        return kills.joinToString(",", "[", "]") { "\"$it\"" }
    }

    override fun recordKill(kill: Kill): Boolean {
        ensureInitialized()

        val sql = """
            INSERT INTO kills (id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                              location_world, location_x, location_y, location_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                kill.id.toString(),
                kill.killerId.toString(),
                kill.victimId.toString(),
                kill.killerGuildId?.toString(),
                kill.victimGuildId?.toString(),
                kill.timestamp.toString(),
                kill.weapon,
                kill.worldId?.toString(),
                kill.location?.x?.toDouble(),
                kill.location?.y?.toDouble(),
                kill.location?.z?.toDouble()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to record kill", e)
        }
    }

    override fun getKillById(killId: UUID): Kill? {
        return kills[killId]
    }

    override fun getKillsByKiller(killerId: UUID, limit: Int): List<Kill> {
        ensureInitialized()

        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            WHERE killer_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, killerId.toString(), limit)
            results.map { mapResultSetToKill(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get kills by killer", e)
        }
    }

    override fun getKillsByVictim(victimId: UUID, limit: Int): List<Kill> {
        ensureInitialized()

        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            WHERE victim_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, victimId.toString(), limit)
            results.map { mapResultSetToKill(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get kills by victim", e)
        }
    }

    override fun getKillsForGuild(guildId: UUID, limit: Int): List<Kill> {
        ensureInitialized()

        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            WHERE killer_guild_id = ? OR victim_guild_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql, guildId.toString(), guildId.toString(), limit)
            results.map { mapResultSetToKill(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get kills for guild", e)
        }
    }

    override fun getKillsBetweenGuilds(guildA: UUID, guildB: UUID, limit: Int): List<Kill> {
        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            WHERE (killer_guild_id = ? AND victim_guild_id = ?) OR (killer_guild_id = ? AND victim_guild_id = ?)
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql,
                guildA.toString(), guildB.toString(),
                guildB.toString(), guildA.toString(),
                limit)
            results.map { mapResultSetToKill(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get kills between guilds", e)
        }
    }

    override fun getGuildKillStats(guildId: UUID): GuildKillStats {
        ensureInitialized()

        return guildStats[guildId] ?: GuildKillStats(guildId)
    }

    override fun updateGuildKillStats(stats: GuildKillStats): Boolean {
        val sql = """
            INSERT OR REPLACE INTO guild_kill_stats
            (guild_id, total_kills, total_deaths, net_kills, kill_death_ratio, last_updated)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                stats.guildId.toString(),
                stats.totalKills,
                stats.totalDeaths,
                stats.netKills,
                stats.killDeathRatio,
                stats.lastUpdated.toString()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update guild kill stats", e)
        }
    }

    override fun getPlayerKillStats(playerId: UUID): PlayerKillStats {
        ensureInitialized()

        return playerStats[playerId] ?: PlayerKillStats(playerId)
    }

    override fun updatePlayerKillStats(stats: PlayerKillStats): Boolean {
        val sql = """
            INSERT OR REPLACE INTO player_kill_stats
            (player_id, guild_id, total_kills, total_deaths, streak, best_streak,
             last_kill_time, last_death_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                stats.playerId.toString(),
                stats.guildId?.toString(),
                stats.totalKills,
                stats.totalDeaths,
                stats.streak,
                stats.bestStreak,
                stats.lastKillTime?.toEpochMilli(),
                stats.lastDeathTime?.toEpochMilli()
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update player kill stats", e)
        }
    }

    override fun getAntiFarmData(playerId: UUID): AntiFarmData {
        return antiFarmData[playerId] ?: AntiFarmData(playerId)
    }

    override fun updateAntiFarmData(data: AntiFarmData): Boolean {
        val sql = """
            INSERT OR REPLACE INTO anti_farm_data
            (player_id, recent_kills, farm_score, last_farm_check, is_currently_farming)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            storage.connection.executeUpdate(sql,
                data.playerId.toString(),
                serializeRecentKills(data.recentKills),
                data.farmScore,
                data.lastFarmCheck.toString(),
                if (data.isCurrentlyFarming) 1 else 0
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to update anti-farm data", e)
        }
    }

    override fun getKillsInPeriod(startTime: Instant, endTime: Instant, limit: Int): List<Kill> {
        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql,
                startTime.toString(),
                endTime.toString(),
                limit)
            results.map { mapResultSetToKill(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get kills in period", e)
        }
    }

    override fun getGuildKillsInPeriod(guildId: UUID, startTime: Instant, endTime: Instant, limit: Int): List<Kill> {
        val sql = """
            SELECT id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon,
                   location_world, location_x, location_y, location_z
            FROM kills
            WHERE (killer_guild_id = ? OR victim_guild_id = ?)
                  AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent()

        return try {
            val results = storage.connection.getResults(sql,
                guildId.toString(),
                guildId.toString(),
                startTime.toString(),
                endTime.toString(),
                limit)
            results.map { mapResultSetToKill(it) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get guild kills in period", e)
        }
    }

    override fun getTotalKillCount(): Int {
        val sql = "SELECT COUNT(*) as count FROM kills"

        return try {
            val result = storage.connection.getFirstRow(sql)
            result?.getInt("count") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get total kill count", e)
        }
    }

    override fun getGuildKillCount(guildId: UUID): Int {
        val sql = "SELECT COUNT(*) as count FROM kills WHERE killer_guild_id = ? OR victim_guild_id = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString(), guildId.toString())
            result?.getInt("count") ?: 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get guild kill count", e)
        }
    }

    override fun deleteOldKills(maxAgeDays: Int): Int {
        val cutoffTime = Instant.now().minusSeconds(maxAgeDays * 24 * 60 * 60L)
        val sql = "DELETE FROM kills WHERE timestamp < ?"

        return try {
            storage.connection.executeUpdate(sql, cutoffTime.toString())
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to delete old kills", e)
        }
    }

    override fun resetGuildKillStats(guildId: UUID): Boolean {
        val sql = "DELETE FROM guild_kill_stats WHERE guild_id = ?"

        return try {
            storage.connection.executeUpdate(sql, guildId.toString()) >= 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to reset guild kill stats", e)
        }
    }

    override fun resetPlayerKillStats(playerId: UUID): Boolean {
        val sql = "DELETE FROM player_kill_stats WHERE player_id = ?"

        return try {
            storage.connection.executeUpdate(sql, playerId.toString()) >= 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to reset player kill stats", e)
        }
    }
}
