package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.DiplomaticHistoryRepository
import net.lumalyte.lg.domain.entities.DiplomaticHistory
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class DiplomaticHistoryRepositorySQLite(private val storage: SQLiteStorage) : DiplomaticHistoryRepository {

    private val logger = LoggerFactory.getLogger(DiplomaticHistoryRepositorySQLite::class.java)

    private val history: MutableMap<UUID, DiplomaticHistory> = mutableMapOf()
    private var isInitialized = false

    init {
        // Defer table creation and preloading until first database access
        // This prevents issues when the database file doesn't exist yet
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            logger.info("Initializing diplomatic history database...")
            try {
                createDiplomaticHistoryTable()
                preload()
                isInitialized = true
                logger.info("Diplomatic history database initialized successfully")
            } catch (e: SQLException) {
                logger.error("Failed to initialize diplomatic history database: ${e.message}", e)
                throw DatabaseOperationException("Failed to initialize diplomatic history database: ${e.message}", e)
            }
        }
    }

    private fun createDiplomaticHistoryTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS diplomatic_history (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                target_guild_id TEXT,
                event_type TEXT NOT NULL,
                description TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                metadata TEXT NOT NULL DEFAULT '{}',
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (target_guild_id) REFERENCES guilds(id) ON DELETE SET NULL
            )
        """.trimIndent()

        try {
            logger.info("Creating diplomatic_history table...")
            storage.connection.executeUpdate(sql)
            logger.info("Successfully created diplomatic_history table")
        } catch (e: SQLException) {
            logger.error("Failed to create diplomatic_history table: ${e.message}", e)
            throw DatabaseOperationException("Failed to create diplomatic_history table: ${e.message}", e)
        }
    }

    private fun preload() {
        val sql = """
            SELECT id, guild_id, target_guild_id, event_type, description, timestamp, metadata
            FROM diplomatic_history
            ORDER BY timestamp DESC
            LIMIT 1000
        """.trimIndent()

        try {
            logger.debug("Preloading diplomatic history from database...")
            val results = storage.connection.getResults(sql)
            var count = 0
            for (result in results) {
                val historyEvent = mapResultSetToDiplomaticHistory(result)
                history[historyEvent.id] = historyEvent
                count++
            }
            logger.info("Successfully preloaded $count diplomatic history events from database")
        } catch (e: SQLException) {
            logger.error("Failed to preload diplomatic history: ${e.message}", e)
            throw DatabaseOperationException("Failed to preload diplomatic history: ${e.message}", e)
        }
    }

    private fun mapResultSetToDiplomaticHistory(rs: co.aikar.idb.DbRow): DiplomaticHistory {
        return DiplomaticHistory(
            id = UUID.fromString(rs.getString("id")),
            guildId = UUID.fromString(rs.getString("guild_id")),
            targetGuildId = rs.getString("target_guild_id")?.let { UUID.fromString(it) },
            eventType = rs.getString("event_type"),
            description = rs.getString("description"),
            timestamp = Instant.parse(rs.getString("timestamp")),
            metadata = rs.getString("metadata")?.let {
                // Simple JSON parsing for metadata - in production this should use a proper JSON library
                emptyMap() // Placeholder for now
            } ?: emptyMap()
        )
    }

    override fun add(history: DiplomaticHistory): Boolean {
        ensureInitialized()

        val sql = """
            INSERT INTO diplomatic_history (id, guild_id, target_guild_id, event_type, description, timestamp, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                history.id.toString(),
                history.guildId.toString(),
                history.targetGuildId?.toString(),
                history.eventType,
                history.description,
                history.timestamp.toString(),
                "{}" // Placeholder for metadata serialization
            )

            this.history[history.id] = history
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Failed to add diplomatic history: ${e.message}", e)
            throw DatabaseOperationException("Failed to add diplomatic history", e)
        }
    }

    override fun getById(historyId: UUID): DiplomaticHistory? {
        return history[historyId]
    }

    override fun getByGuild(guildId: UUID): List<DiplomaticHistory> {
        ensureInitialized()
        return history.values.filter { it.guildId == guildId }
            .sortedByDescending { it.timestamp }
    }

    override fun getByGuildAndType(guildId: UUID, eventType: String): List<DiplomaticHistory> {
        return history.values.filter { it.guildId == guildId && it.eventType == eventType }
            .sortedByDescending { it.timestamp }
    }

    override fun getByGuildAndTimeRange(guildId: UUID, startTime: Instant, endTime: Instant): List<DiplomaticHistory> {
        return history.values.filter {
            it.guildId == guildId && it.timestamp in startTime..endTime
        }.sortedByDescending { it.timestamp }
    }

    override fun getBetweenGuilds(guildA: UUID, guildB: UUID): List<DiplomaticHistory> {
        return history.values.filter {
            (it.guildId == guildA && it.targetGuildId == guildB) ||
            (it.guildId == guildB && it.targetGuildId == guildA)
        }.sortedByDescending { it.timestamp }
    }

    override fun getAll(): List<DiplomaticHistory> {
        return history.values.toList().sortedByDescending { it.timestamp }
    }

    override fun getRecentByGuild(guildId: UUID, limit: Int): List<DiplomaticHistory> {
        return getByGuild(guildId).take(limit)
    }

    override fun getByType(eventType: String): List<DiplomaticHistory> {
        return history.values.filter { it.eventType == eventType }
            .sortedByDescending { it.timestamp }
    }

    override fun cleanupOldEvents(olderThan: Instant): Int {
        val oldEvents = history.values.filter { it.timestamp.isBefore(olderThan) }
        oldEvents.forEach { history.remove(it.id) }

        if (oldEvents.isNotEmpty()) {
            val sql = "DELETE FROM diplomatic_history WHERE timestamp < ?"
            try {
                storage.connection.executeUpdate(sql, olderThan.toString())
                logger.info("Cleaned up ${oldEvents.size} old diplomatic history events")
            } catch (e: SQLException) {
                logger.error("Failed to cleanup old diplomatic history events: ${e.message}", e)
            }
        }

        return oldEvents.size
    }
}
