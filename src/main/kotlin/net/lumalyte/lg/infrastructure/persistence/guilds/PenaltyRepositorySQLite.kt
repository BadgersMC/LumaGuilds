package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.PenaltyRepository
import net.lumalyte.lg.domain.entities.GuildPenalty
import net.lumalyte.lg.domain.entities.PenaltyType
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/**
 * SQLite/MariaDB implementation of [PenaltyRepository]. Portable SQL only —
 * works on both backends.
 */
class PenaltyRepositorySQLite(
    private val storage: Storage<Database>
) : PenaltyRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        createTable()
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_penalties (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id TEXT NOT NULL,
                penalty_type TEXT NOT NULL,
                amount INTEGER,
                reason TEXT,
                actor_uuid TEXT NOT NULL,
                actor_name TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent()

        val guildIndexSql = "CREATE INDEX IF NOT EXISTS idx_guild_penalties_guild ON guild_penalties(guild_id)"

        try {
            storage.connection.executeUpdate(sql)
            storage.connection.executeUpdate(guildIndexSql)
        } catch (e: SQLException) {
            logger.error("Failed to create guild_penalties table", e)
        }
    }

    override fun recordPenalty(penalty: GuildPenalty): Boolean {
        val sql = """
            INSERT INTO guild_penalties (guild_id, penalty_type, amount, reason, actor_uuid, actor_name, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return try {
            storage.connection.executeUpdate(
                sql,
                penalty.guildId.toString(),
                penalty.type.name,
                penalty.amount,
                penalty.reason,
                penalty.actorUuid.toString(),
                penalty.actorName,
                penalty.createdAt.toEpochMilli()
            ) > 0
        } catch (e: SQLException) {
            logger.error("Failed to record penalty for guild {}", penalty.guildId, e)
            false
        }
    }

    override fun getByGuild(guildId: UUID): List<GuildPenalty> {
        val sql = """
            SELECT id, guild_id, penalty_type, amount, reason, actor_uuid, actor_name, created_at
            FROM guild_penalties
            WHERE guild_id = ?
            ORDER BY created_at DESC
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, guildId.toString()).mapNotNull { it.toPenalty() }
        } catch (e: SQLException) {
            logger.error("Failed to load penalties for guild {}", guildId, e)
            emptyList()
        }
    }

    override fun hasActiveMute(guildId: UUID, now: Instant): Boolean {
        val sql = """
            SELECT 1 AS found FROM guild_penalties
            WHERE guild_id = ? AND penalty_type = 'GUILD_MUTE'
              AND amount IS NOT NULL AND amount > 0
              AND (created_at + amount) > ?
            LIMIT 1
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, guildId.toString(), now.toEpochMilli()).isNotEmpty()
        } catch (e: SQLException) {
            logger.error("Failed to check active mute for guild {}", guildId, e)
            false
        }
    }

    override fun getLatest(guildId: UUID): GuildPenalty? {
        val sql = """
            SELECT id, guild_id, penalty_type, amount, reason, actor_uuid, actor_name, created_at
            FROM guild_penalties
            WHERE guild_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, guildId.toString()).firstOrNull()?.toPenalty()
        } catch (e: SQLException) {
            logger.error("Failed to load latest penalty for guild {}", guildId, e)
            null
        }
    }

    private fun co.aikar.idb.DbRow.toPenalty(): GuildPenalty? {
        return runCatching {
            GuildPenalty(
                id = getLong("id") ?: 0L,
                guildId = UUID.fromString(getString("guild_id")),
                type = PenaltyType.entries.firstOrNull { it.name == getString("penalty_type") }
                    ?: PenaltyType.DISBAND,
                amount = getLong("amount"),
                reason = getString("reason"),
                actorUuid = UUID.fromString(getString("actor_uuid")),
                actorName = getString("actor_name") ?: "Unknown",
                createdAt = Instant.ofEpochMilli(getLong("created_at") ?: 0L)
            )
        }.getOrElse { e ->
            logger.warn("Skipping malformed penalty row: {}", e.message)
            null
        }
    }
}
