package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.DiplomaticRequestRepository
import net.lumalyte.lg.domain.entities.DiplomaticRequest
import net.lumalyte.lg.domain.entities.DiplomaticRequestType
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class DiplomaticRequestRepositorySQLite(private val storage: SQLiteStorage) : DiplomaticRequestRepository {

    private val logger = LoggerFactory.getLogger(DiplomaticRequestRepositorySQLite::class.java)

    private val requests: MutableMap<UUID, DiplomaticRequest> = mutableMapOf()
    private var isInitialized = false

    init {
        // Defer table creation and preloading until first database access
        // This prevents issues when the database file doesn't exist yet
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            logger.info("Initializing diplomatic requests database...")
            try {
                createDiplomaticRequestsTable()
                preload()
                isInitialized = true
                logger.info("Diplomatic requests database initialized successfully")
            } catch (e: SQLException) {
                logger.error("Failed to initialize diplomatic requests database: ${e.message}", e)
                throw DatabaseOperationException("Failed to initialize diplomatic requests database: ${e.message}", e)
            }
        }
    }

    private fun createDiplomaticRequestsTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS diplomatic_requests (
                id TEXT PRIMARY KEY,
                from_guild_id TEXT NOT NULL,
                to_guild_id TEXT NOT NULL,
                type TEXT NOT NULL,
                message TEXT,
                requested_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                metadata TEXT NOT NULL DEFAULT '{}',
                UNIQUE(from_guild_id, to_guild_id, type),
                FOREIGN KEY (from_guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (to_guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent()

        try {
            logger.info("Creating diplomatic_requests table...")
            storage.connection.executeUpdate(sql)
            logger.info("Successfully created diplomatic_requests table")
        } catch (e: SQLException) {
            logger.error("Failed to create diplomatic_requests table: ${e.message}", e)
            throw DatabaseOperationException("Failed to create diplomatic_requests table: ${e.message}", e)
        }
    }

    private fun preload() {
        val sql = """
            SELECT id, from_guild_id, to_guild_id, type, message, requested_at, expires_at, metadata
            FROM diplomatic_requests
        """.trimIndent()

        try {
            logger.debug("Preloading diplomatic requests from database...")
            val results = storage.connection.getResults(sql)
            var count = 0
            for (result in results) {
                val request = mapResultSetToDiplomaticRequest(result)
                requests[request.id] = request
                count++
            }
            logger.info("Successfully preloaded $count diplomatic requests from database")
        } catch (e: SQLException) {
            logger.error("Failed to preload diplomatic requests: ${e.message}", e)
            throw DatabaseOperationException("Failed to preload diplomatic requests: ${e.message}", e)
        }
    }

    private fun mapResultSetToDiplomaticRequest(rs: co.aikar.idb.DbRow): DiplomaticRequest {
        return DiplomaticRequest(
            id = UUID.fromString(rs.getString("id")),
            fromGuildId = UUID.fromString(rs.getString("from_guild_id")),
            toGuildId = UUID.fromString(rs.getString("to_guild_id")),
            type = DiplomaticRequestType.valueOf(rs.getString("type")),
            message = rs.getString("message"),
            requestedAt = Instant.parse(rs.getString("requested_at")),
            expiresAt = Instant.parse(rs.getString("expires_at")),
            metadata = rs.getString("metadata")?.let {
                // Simple JSON parsing for metadata - in production this should use a proper JSON library
                emptyMap() // Placeholder for now
            } ?: emptyMap()
        )
    }

    override fun add(request: DiplomaticRequest): Boolean {
        ensureInitialized()

        val sql = """
            INSERT INTO diplomatic_requests (id, from_guild_id, to_guild_id, type, message, requested_at, expires_at, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                request.id.toString(),
                request.fromGuildId.toString(),
                request.toGuildId.toString(),
                request.type.name,
                request.message,
                request.requestedAt.toString(),
                request.expiresAt.toString(),
                "{}" // Placeholder for metadata serialization
            )

            requests[request.id] = request
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Failed to add diplomatic request: ${e.message}", e)
            throw DatabaseOperationException("Failed to add diplomatic request", e)
        }
    }

    override fun update(request: DiplomaticRequest): Boolean {
        val sql = """
            UPDATE diplomatic_requests
            SET from_guild_id = ?, to_guild_id = ?, type = ?, message = ?, requested_at = ?, expires_at = ?, metadata = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                request.fromGuildId.toString(),
                request.toGuildId.toString(),
                request.type.name,
                request.message,
                request.requestedAt.toString(),
                request.expiresAt.toString(),
                "{}",
                request.id.toString()
            )

            if (rowsAffected > 0) {
                requests[request.id] = request
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to update diplomatic request: ${e.message}", e)
            throw DatabaseOperationException("Failed to update diplomatic request", e)
        }
    }

    override fun remove(requestId: UUID): Boolean {
        val sql = "DELETE FROM diplomatic_requests WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, requestId.toString())

            if (rowsAffected > 0) {
                requests.remove(requestId)
                true
            } else {
                false
            }
        } catch (e: SQLException) {
            logger.error("Failed to remove diplomatic request: ${e.message}", e)
            throw DatabaseOperationException("Failed to remove diplomatic request", e)
        }
    }

    override fun getById(requestId: UUID): DiplomaticRequest? {
        return requests[requestId]
    }

    override fun getByGuild(guildId: UUID): List<DiplomaticRequest> {
        ensureInitialized()
        return requests.values.filter { it.fromGuildId == guildId || it.toGuildId == guildId }
    }

    override fun getIncomingRequests(guildId: UUID): List<DiplomaticRequest> {
        ensureInitialized()
        return requests.values.filter { it.toGuildId == guildId && it.isActive() }
    }

    override fun getOutgoingRequests(guildId: UUID): List<DiplomaticRequest> {
        ensureInitialized()
        return requests.values.filter { it.fromGuildId == guildId && it.isActive() }
    }

    override fun getByType(guildId: UUID, type: DiplomaticRequestType): List<DiplomaticRequest> {
        return requests.values.filter {
            (it.fromGuildId == guildId || it.toGuildId == guildId) &&
            it.type == type && it.isActive()
        }
    }

    override fun getActiveRequests(guildId: UUID): List<DiplomaticRequest> {
        return requests.values.filter {
            (it.fromGuildId == guildId || it.toGuildId == guildId) && it.isActive()
        }
    }

    override fun getExpiredRequests(): List<DiplomaticRequest> {
        val now = Instant.now()
        return requests.values.filter { it.expiresAt.isBefore(now) }
    }

    override fun getBetweenGuilds(guildA: UUID, guildB: UUID): List<DiplomaticRequest> {
        return requests.values.filter {
            (it.fromGuildId == guildA && it.toGuildId == guildB) ||
            (it.fromGuildId == guildB && it.toGuildId == guildA)
        }
    }

    override fun getAll(): List<DiplomaticRequest> {
        return requests.values.toList()
    }
}
