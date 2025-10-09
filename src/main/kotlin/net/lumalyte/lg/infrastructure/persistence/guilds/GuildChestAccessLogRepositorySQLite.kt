package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildChestAccessLogRepository
import net.lumalyte.lg.domain.entities.GuildChestAccessLog
import net.lumalyte.lg.domain.entities.GuildChestAction
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class GuildChestAccessLogRepositorySQLite(private val storage: SQLiteStorage) : GuildChestAccessLogRepository {

    private val logger = LoggerFactory.getLogger(GuildChestAccessLogRepositorySQLite::class.java)

    private val accessLogs: MutableMap<UUID, GuildChestAccessLog> = mutableMapOf()
    private var isInitialized = false

    init {
        // Defer table creation and preloading until first database access
        // This prevents issues when the database file doesn't exist yet
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            logger.info("Initializing guild chest access log database...")
            try {
                createGuildChestAccessLogTable()
                preload()
                isInitialized = true
                logger.info("Guild chest access log database initialized successfully")
            } catch (e: SQLException) {
                logger.error("Failed to initialize guild chest access log database: ${e.message}", e)
                throw DatabaseOperationException("Failed to initialize guild chest access log database: ${e.message}", e)
            }
        }
    }

    private fun createGuildChestAccessLogTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_chest_access_logs (
                id TEXT PRIMARY KEY,
                chest_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                action TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                item_type TEXT,
                item_amount INTEGER DEFAULT 0,
                metadata TEXT NOT NULL DEFAULT '{}',
                FOREIGN KEY (chest_id) REFERENCES guild_chests(id) ON DELETE CASCADE
            )
        """.trimIndent()

        try {
            logger.info("Creating guild_chest_access_logs table...")
            storage.connection.executeUpdate(sql)
            logger.info("Successfully created guild_chest_access_logs table")
        } catch (e: SQLException) {
            logger.error("Failed to create guild_chest_access_logs table: ${e.message}", e)
            throw DatabaseOperationException("Failed to create guild_chest_access_logs table: ${e.message}", e)
        }
    }

    private fun preload() {
        val sql = """
            SELECT id, chest_id, player_id, action, timestamp, item_type, item_amount, metadata
            FROM guild_chest_access_logs
            ORDER BY timestamp DESC
            LIMIT 1000
        """.trimIndent()

        try {
            logger.debug("Preloading guild chest access logs from database...")
            val results = storage.connection.getResults(sql)
            var count = 0
            for (result in results) {
                val log = mapResultSetToGuildChestAccessLog(result)
                accessLogs[log.id] = log
                count++
            }
            logger.info("Successfully preloaded $count guild chest access logs from database")
        } catch (e: SQLException) {
            logger.error("Failed to preload guild chest access logs: ${e.message}", e)
            throw DatabaseOperationException("Failed to preload guild chest access logs: ${e.message}", e)
        }
    }

    private fun mapResultSetToGuildChestAccessLog(rs: co.aikar.idb.DbRow): GuildChestAccessLog {
        return GuildChestAccessLog(
            id = UUID.fromString(rs.getString("id")),
            chestId = UUID.fromString(rs.getString("chest_id")),
            playerId = UUID.fromString(rs.getString("player_id")),
            action = GuildChestAction.valueOf(rs.getString("action")),
            timestamp = Instant.parse(rs.getString("timestamp")),
            itemType = rs.getString("item_type"),
            itemAmount = rs.getInt("item_amount"),
            metadata = rs.getString("metadata")?.let {
                // Simple JSON parsing for metadata - in production this should use a proper JSON library
                emptyMap() // Placeholder for now
            } ?: emptyMap()
        )
    }

    override fun add(log: GuildChestAccessLog): Boolean {
        ensureInitialized()

        val sql = """
            INSERT INTO guild_chest_access_logs (id, chest_id, player_id, action, timestamp, item_type, item_amount, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                log.id.toString(),
                log.chestId.toString(),
                log.playerId.toString(),
                log.action.name,
                log.timestamp.toString(),
                log.itemType,
                log.itemAmount,
                "{}" // Placeholder for metadata serialization
            )

            accessLogs[log.id] = log
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Failed to add guild chest access log: ${e.message}", e)
            throw DatabaseOperationException("Failed to add guild chest access log", e)
        }
    }

    override fun getById(logId: UUID): GuildChestAccessLog? {
        return accessLogs[logId]
    }

    override fun getByChest(chestId: UUID): List<GuildChestAccessLog> {
        ensureInitialized()
        return accessLogs.values.filter { it.chestId == chestId }
            .sortedByDescending { it.timestamp }
    }

    override fun getByPlayer(playerId: UUID): List<GuildChestAccessLog> {
        return accessLogs.values.filter { it.playerId == playerId }
            .sortedByDescending { it.timestamp }
    }

    override fun getByAction(action: GuildChestAction): List<GuildChestAccessLog> {
        return accessLogs.values.filter { it.action == action }
            .sortedByDescending { it.timestamp }
    }

    override fun getByTimeRange(startTime: Instant, endTime: Instant): List<GuildChestAccessLog> {
        return accessLogs.values.filter { it.timestamp in startTime..endTime }
            .sortedByDescending { it.timestamp }
    }

    override fun getRecentByChest(chestId: UUID, limit: Int): List<GuildChestAccessLog> {
        return getByChest(chestId).take(limit)
    }

    override fun getSuspiciousActivities(): List<GuildChestAccessLog> {
        return accessLogs.values.filter { it.action == GuildChestAction.BREAK_ATTEMPT }
            .sortedByDescending { it.timestamp }
    }

    override fun cleanupOldLogs(olderThan: Instant): Int {
        val oldLogs = accessLogs.values.filter { it.timestamp.isBefore(olderThan) }
        oldLogs.forEach { accessLogs.remove(it.id) }

        if (oldLogs.isNotEmpty()) {
            val sql = "DELETE FROM guild_chest_access_logs WHERE timestamp < ?"
            try {
                storage.connection.executeUpdate(sql, olderThan.toString())
                logger.info("Cleaned up ${oldLogs.size} old guild chest access logs")
            } catch (e: SQLException) {
                logger.error("Failed to cleanup old guild chest access logs: ${e.message}", e)
            }
        }

        return oldLogs.size
    }

    override fun getByGuild(guildId: UUID): List<GuildChestAccessLog> {
        // This would require a more complex query to get logs for all chests of a guild
        // For now, return empty list - this could be implemented with a join query
        return emptyList()
    }
}
