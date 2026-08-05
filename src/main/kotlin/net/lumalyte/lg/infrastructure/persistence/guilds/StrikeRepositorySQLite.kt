package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.StrikeRepository
import net.lumalyte.lg.domain.entities.GuildStrike
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/**
 * SQLite/MariaDB implementation of [StrikeRepository].
 *
 * Works against both backends — the SQL used here is portable (no SQLite-only
 * syntax like INSERT OR IGNORE / ON CONFLICT). Dedupe is done by checking the
 * LiteBans entry id before inserting.
 */
class StrikeRepositorySQLite(
    private val storage: Storage<Database>
) : StrikeRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        createTable()
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_strikes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                player_name TEXT,
                punishment_type TEXT NOT NULL,
                reason TEXT,
                executor_name TEXT,
                issued_at INTEGER NOT NULL,
                litebans_entry_id INTEGER,
                active INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent()

        val guildIndexSql = "CREATE INDEX IF NOT EXISTS idx_guild_strikes_guild ON guild_strikes(guild_id)"
        val entryIndexSql = "CREATE INDEX IF NOT EXISTS idx_guild_strikes_entry ON guild_strikes(litebans_entry_id)"

        try {
            storage.connection.executeUpdate(sql)
            storage.connection.executeUpdate(guildIndexSql)
            storage.connection.executeUpdate(entryIndexSql)
        } catch (e: SQLException) {
            logger.error("Failed to create guild_strikes table", e)
        }
    }

    override fun recordStrike(strike: GuildStrike): Boolean {
        // Dedupe: LiteBans can re-fire entryAdded for the same punishment
        // (cross-server sync, reloads). Only insert if not already recorded.
        val entryId = strike.litebansEntryId
        if (entryId != null && existsByEntryId(entryId)) {
            return false
        }

        val sql = """
            INSERT INTO guild_strikes (guild_id, player_uuid, player_name, punishment_type,
                                       reason, executor_name, issued_at, litebans_entry_id, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rows = storage.connection.executeUpdate(
                sql,
                strike.guildId.toString(),
                strike.playerUuid.toString(),
                strike.playerName,
                strike.punishmentType,
                strike.reason,
                strike.executorName,
                strike.issuedAt.toEpochMilli(),
                entryId,
                if (strike.active) 1 else 0
            )
            rows > 0
        } catch (e: SQLException) {
            logger.error("Failed to record strike for guild {}", strike.guildId, e)
            false
        }
    }

    override fun deactivateStrike(litebansEntryId: Long): Boolean {
        val sql = "UPDATE guild_strikes SET active = 0 WHERE litebans_entry_id = ?"
        return try {
            storage.connection.executeUpdate(sql, litebansEntryId) > 0
        } catch (e: SQLException) {
            logger.error("Failed to deactivate strike entry {}", litebansEntryId, e)
            false
        }
    }

    override fun countByGuild(guildId: UUID): Int {
        val sql = "SELECT COUNT(*) AS cnt FROM guild_strikes WHERE guild_id = ?"
        return try {
            val results = storage.connection.getResults(sql, guildId.toString())
            results.firstOrNull()?.getInt("cnt") ?: 0
        } catch (e: SQLException) {
            logger.error("Failed to count strikes for guild {}", guildId, e)
            0
        }
    }

    override fun getByGuild(guildId: UUID): List<GuildStrike> {
        val sql = """
            SELECT id, guild_id, player_uuid, player_name, punishment_type, reason,
                   executor_name, issued_at, litebans_entry_id, active
            FROM guild_strikes
            WHERE guild_id = ?
            ORDER BY issued_at DESC
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, guildId.toString()).mapNotNull { it.toStrike() }
        } catch (e: SQLException) {
            logger.error("Failed to load strikes for guild {}", guildId, e)
            emptyList()
        }
    }

    override fun getAllCounts(): Map<UUID, Int> {
        val sql = """
            SELECT guild_id, COUNT(*) AS cnt
            FROM guild_strikes
            GROUP BY guild_id
            ORDER BY cnt DESC
        """.trimIndent()
        return try {
            storage.connection.getResults(sql).mapNotNull { row ->
                val guildId = runCatching { UUID.fromString(row.getString("guild_id")) }.getOrNull() ?: return@mapNotNull null
                guildId to row.getInt("cnt")
            }.toMap()
        } catch (e: SQLException) {
            logger.error("Failed to load all strike counts", e)
            emptyMap()
        }
    }

    override fun countAll(): Int {
        val sql = "SELECT COUNT(*) AS cnt FROM guild_strikes"
        return try {
            storage.connection.getResults(sql).firstOrNull()?.getInt("cnt") ?: 0
        } catch (e: SQLException) {
            logger.error("Failed to count all strikes", e)
            0
        }
    }

    private fun existsByEntryId(entryId: Long): Boolean {
        val sql = "SELECT 1 AS found FROM guild_strikes WHERE litebans_entry_id = ? LIMIT 1"
        return try {
            storage.connection.getResults(sql, entryId).isNotEmpty()
        } catch (e: SQLException) {
            logger.error("Failed to check strike entry {}", entryId, e)
            false
        }
    }

    private fun co.aikar.idb.DbRow.toStrike(): GuildStrike? {
        return runCatching {
            GuildStrike(
                id = getLong("id") ?: 0L,
                guildId = UUID.fromString(getString("guild_id")),
                playerUuid = UUID.fromString(getString("player_uuid")),
                playerName = getString("player_name"),
                punishmentType = getString("punishment_type"),
                reason = getString("reason"),
                executorName = getString("executor_name"),
                issuedAt = Instant.ofEpochMilli(getLong("issued_at") ?: 0L),
                litebansEntryId = getLong("litebans_entry_id"),
                active = (getInt("active") ?: 1) == 1
            )
        }.getOrElse { e ->
            logger.warn("Skipping malformed strike row: {}", e.message)
            null
        }
    }
}
