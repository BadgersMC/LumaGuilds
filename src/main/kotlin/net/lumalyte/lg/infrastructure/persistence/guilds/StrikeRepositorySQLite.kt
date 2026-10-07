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

class StrikeRepositorySQLite(
    private val storage: Storage<Database>,
) : StrikeRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordStrike(strike: GuildStrike): Boolean {
        val entryId = strike.litebansEntryId
        if (entryId != null && existsByTypeAndEntryId(strike.punishmentType, entryId)) return false
        return insertStrike(strike, strict = false)
    }

    override fun recordExternalStrike(strike: GuildStrike): Boolean {
        val provider = requireNotNull(strike.sourceProvider)
        val sourceId = requireNotNull(strike.sourcePunishmentId)
        require(provider.isNotBlank() && sourceId.isNotBlank())
        if (existsBySource(provider, sourceId)) return false
        return insertStrike(strike, strict = true)
    }

    private fun insertStrike(strike: GuildStrike, strict: Boolean): Boolean {
        val sql = """
            INSERT INTO guild_strikes (
                guild_id, player_uuid, player_name, punishment_type,
                reason, executor_name, issued_at, litebans_entry_id,
                source_provider, source_punishment_id, expires_at, active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return try {
            storage.connection.executeUpdate(
                sql,
                strike.guildId.toString(),
                strike.playerUuid.toString(),
                strike.playerName,
                strike.punishmentType,
                strike.reason,
                strike.executorName,
                strike.issuedAt.toEpochMilli(),
                strike.litebansEntryId,
                strike.sourceProvider,
                strike.sourcePunishmentId,
                strike.expiresAt?.toEpochMilli(),
                if (strike.active) 1 else 0,
            ) > 0
        } catch (e: SQLException) {
            if (isDuplicate(e)) {
                false
            } else if (strict) {
                throw DatabaseOperationException("Failed to record external guild strike", e)
            } else {
                logger.error("Failed to record strike for guild {}", strike.guildId, e)
                false
            }
        }
    }

    override fun deactivateStrike(punishmentType: String, litebansEntryId: Long): Boolean =
        try {
            reconcileLegacyStrike(punishmentType, litebansEntryId, active = false)
        } catch (e: DatabaseOperationException) {
            logger.error("Failed to deactivate strike {} entry {}", punishmentType, litebansEntryId, e)
            false
        }

    override fun reconcileLegacyStrike(
        punishmentType: String,
        litebansEntryId: Long,
        active: Boolean,
    ): Boolean {
        try {
            return storage.connection.executeUpdate(
                "UPDATE guild_strikes SET active = ? WHERE punishment_type = ? AND litebans_entry_id = ?",
                if (active) 1 else 0,
                punishmentType,
                litebansEntryId,
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to reconcile legacy guild strike", e)
        }
    }

    override fun reconcileExternalStrike(
        sourceProvider: String,
        sourcePunishmentId: String,
        active: Boolean,
        expiresAt: Instant?,
    ): Boolean {
        try {
            val updated = storage.connection.executeUpdate(
                """
                UPDATE guild_strikes SET active = ?, expires_at = ?
                WHERE source_provider = ? AND source_punishment_id = ?
                  AND (active <> ? OR expires_at <> ? OR (expires_at IS NULL) <> (? IS NULL))
                """.trimIndent(),
                if (active) 1 else 0,
                expiresAt?.toEpochMilli(),
                sourceProvider,
                sourcePunishmentId,
                if (active) 1 else 0,
                expiresAt?.toEpochMilli(),
                expiresAt?.toEpochMilli(),
            )
            return updated > 0 || existsBySource(sourceProvider, sourcePunishmentId)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to reconcile external guild strike", e)
        }
    }

    override fun deactivateExpiredExternal(now: Instant): Int {
        try {
            return storage.connection.executeUpdate(
                """
                UPDATE guild_strikes SET active = 0
                WHERE source_provider IS NOT NULL
                  AND active = 1
                  AND expires_at IS NOT NULL
                  AND expires_at <= ?
                """.trimIndent(),
                now.toEpochMilli(),
            )
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to expire external guild strikes", e)
        }
    }

    override fun countByGuild(guildId: UUID): Int =
        count("SELECT COUNT(*) AS cnt FROM guild_strikes WHERE guild_id = ?", guildId)

    override fun countActiveByGuild(guildId: UUID): Int =
        count("SELECT COUNT(*) AS cnt FROM guild_strikes WHERE guild_id = ? AND active = 1", guildId)

    private fun count(sql: String, guildId: UUID): Int =
        try {
            storage.connection.getResults(sql, guildId.toString()).firstOrNull()?.getInt("cnt") ?: 0
        } catch (e: SQLException) {
            logger.error("Failed to count strikes for guild {}", guildId, e)
            0
        }

    override fun getByGuild(guildId: UUID): List<GuildStrike> {
        val sql = """
            SELECT id, guild_id, player_uuid, player_name, punishment_type, reason,
                   executor_name, issued_at, litebans_entry_id, source_provider,
                   source_punishment_id, expires_at, active
            FROM guild_strikes WHERE guild_id = ? ORDER BY issued_at DESC
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, guildId.toString()).mapNotNull { it.toStrike() }
        } catch (e: SQLException) {
            logger.error("Failed to load strikes for guild {}", guildId, e)
            emptyList()
        }
    }

    override fun getAllCounts(): Map<UUID, Int> = groupedCounts(false)
    override fun getAllActiveCounts(): Map<UUID, Int> = groupedCounts(true)

    private fun groupedCounts(activeOnly: Boolean): Map<UUID, Int> {
        val where = if (activeOnly) "WHERE active = 1" else ""
        return try {
            storage.connection.getResults(
                "SELECT guild_id, COUNT(*) AS cnt FROM guild_strikes $where GROUP BY guild_id ORDER BY cnt DESC",
            ).mapNotNull { row ->
                runCatching { UUID.fromString(row.getString("guild_id")) }.getOrNull()?.let {
                    it to row.getInt("cnt")
                }
            }.toMap()
        } catch (e: SQLException) {
            logger.error("Failed to load strike counts", e)
            emptyMap()
        }
    }

    override fun countAll(): Int =
        try {
            storage.connection.getResults("SELECT COUNT(*) AS cnt FROM guild_strikes")
                .firstOrNull()?.getInt("cnt") ?: 0
        } catch (e: SQLException) {
            logger.error("Failed to count all strikes", e)
            0
        }

    private fun existsByTypeAndEntryId(punishmentType: String, entryId: Long): Boolean =
        try {
            storage.connection.getResults(
                "SELECT 1 AS found FROM guild_strikes WHERE punishment_type = ? AND litebans_entry_id = ? LIMIT 1",
                punishmentType, entryId,
            ).isNotEmpty()
        } catch (e: SQLException) {
            logger.error("Failed to check strike {} entry {}", punishmentType, entryId, e)
            false
        }

    private fun existsBySource(provider: String, sourceId: String): Boolean =
        try {
            storage.connection.getResults(
                """
                SELECT 1 AS found FROM guild_strikes
                WHERE source_provider = ? AND source_punishment_id = ? LIMIT 1
                """.trimIndent(),
                provider, sourceId,
            ).isNotEmpty()
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to check external guild strike dedupe key", e)
        }

    private fun co.aikar.idb.DbRow.toStrike(): GuildStrike? =
        runCatching {
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
                sourceProvider = getString("source_provider"),
                sourcePunishmentId = getString("source_punishment_id"),
                expiresAt = getLong("expires_at")?.let(Instant::ofEpochMilli),
                active = (getInt("active") ?: 1) == 1,
            )
        }.getOrElse { e ->
            logger.warn("Skipping malformed strike row: {}", e.message)
            null
        }

    private fun isDuplicate(error: SQLException): Boolean =
        error.message.orEmpty().contains("UNIQUE", ignoreCase = true) ||
            error.message.orEmpty().contains("duplicate", ignoreCase = true)
}
