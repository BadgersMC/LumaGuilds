package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.infrastructure.persistence.getInstant
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class MembershipHistoryRepositorySQLite(
    private val storage: Storage<Database>
) : MembershipHistoryRepository {

    private val logger = LoggerFactory.getLogger(MembershipHistoryRepositorySQLite::class.java)
    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized) {
            createTable()
            isInitialized = true
        }
    }

    private fun createTable() {
        // MariaDB gets this table and its index from MariaDBMigrations; the SQLite DDL below
        // (two statements, TEXT keys) is a syntax error there and broke every join on MariaDB.
        if (storage.dialect == net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect.MARIADB) return
        val table = """
            CREATE TABLE IF NOT EXISTS membership_history (
                id TEXT PRIMARY KEY,
                player_id TEXT NOT NULL,
                guild_id TEXT NOT NULL,
                joined_at TEXT NOT NULL,
                departed_at TEXT,
                departure_reason TEXT,
                recruit_xp_awarded_at TEXT
            )
        """.trimIndent()
        val index = "CREATE INDEX IF NOT EXISTS idx_membership_history_player ON membership_history(player_id)"
        try {
            storage.connection.executeUpdate(table)
            storage.connection.executeUpdate(index)
            logger.info("membership_history table ready")
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create membership_history table", e)
        }
    }

    override fun openStint(playerId: UUID, guildId: UUID): Boolean {
        ensureInitialized()
        val sql = """
            INSERT INTO membership_history (id, player_id, guild_id, joined_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        return try {
            val rows = storage.connection.executeUpdate(
                sql,
                UUID.randomUUID().toString(),
                playerId.toString(),
                guildId.toString(),
                Instant.now().toString()
            )
            rows > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to open membership stint for $playerId in $guildId", e)
        }
    }

    override fun closeStint(playerId: UUID, guildId: UUID, reason: DepartureReason): Boolean {
        ensureInitialized()
        val sql = """
            UPDATE membership_history
            SET departed_at = ?, departure_reason = ?
            WHERE player_id = ? AND guild_id = ? AND departed_at IS NULL
        """.trimIndent()
        return try {
            val rows = storage.connection.executeUpdate(
                sql,
                Instant.now().toString(),
                reason.name,
                playerId.toString(),
                guildId.toString()
            )
            rows > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to close membership stint for $playerId in $guildId", e)
        }
    }

    override fun getByPlayer(playerId: UUID): List<MembershipHistory> {
        ensureInitialized()
        val sql = """
            SELECT id, player_id, guild_id, joined_at, departed_at, departure_reason, recruit_xp_awarded_at
            FROM membership_history
            WHERE player_id = ?
            ORDER BY joined_at ASC
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, playerId.toString()).map { row ->
                row.toMembershipHistory()
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to fetch membership history for $playerId", e)
        }
    }

    override fun getByGuild(guildId: UUID): List<MembershipHistory> {
        ensureInitialized()
        return try {
            storage.connection.getResults(
                """
                SELECT id, player_id, guild_id, joined_at, departed_at, departure_reason, recruit_xp_awarded_at
                FROM membership_history
                WHERE guild_id = ?
                ORDER BY joined_at ASC
                """.trimIndent(),
                guildId.toString(),
            ).map { it.toMembershipHistory() }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to fetch membership history for guild $guildId", e)
        }
    }

    override fun getQualifiedUnawarded(joinedAtOrBefore: Instant): List<MembershipHistory> {
        ensureInitialized()
        return try {
            storage.connection.getResults(
                """
                SELECT id, player_id, guild_id, joined_at, departed_at, departure_reason, recruit_xp_awarded_at
                FROM membership_history
                WHERE departed_at IS NULL AND recruit_xp_awarded_at IS NULL AND joined_at <= ?
                ORDER BY joined_at ASC
                """.trimIndent(),
                joinedAtOrBefore.toString(),
            ).map { it.toMembershipHistory() }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to fetch qualified recruit stints", e)
        }
    }

    override fun markRecruitXpAwarded(stintId: UUID, awardedAt: Instant): Boolean {
        ensureInitialized()
        return try {
            storage.connection.executeUpdate(
                "UPDATE membership_history SET recruit_xp_awarded_at = ? WHERE id = ? AND departed_at IS NULL AND recruit_xp_awarded_at IS NULL",
                awardedAt.toString(),
                stintId.toString(),
            ) > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to mark recruit XP for stint $stintId", e)
        }
    }

    private fun co.aikar.idb.DbRow.toMembershipHistory() = MembershipHistory(
        id = UUID.fromString(getString("id")),
        playerId = UUID.fromString(getString("player_id")),
        guildId = UUID.fromString(getString("guild_id")),
        joinedAt = getInstantNotNull("joined_at"),
        departedAt = getInstant("departed_at"),
        departureReason = getString("departure_reason")?.let { DepartureReason.valueOf(it) },
        recruitXpAwardedAt = getInstant("recruit_xp_awarded_at"),
    )
}
