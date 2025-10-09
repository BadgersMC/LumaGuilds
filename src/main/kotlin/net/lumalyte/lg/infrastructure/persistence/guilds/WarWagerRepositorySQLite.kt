package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.WarWagerRepository
import net.lumalyte.lg.domain.entities.WarWager
import net.lumalyte.lg.domain.entities.WagerStatus
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class WarWagerRepositorySQLite(private val storage: SQLiteStorage) : WarWagerRepository {

    private val wagers: MutableMap<UUID, WarWager> = mutableMapOf()

    init {
        createWarWagerTable()
        preload()
    }

    private fun createWarWagerTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS war_wagers (
                id TEXT PRIMARY KEY,
                war_id TEXT NOT NULL UNIQUE,
                declaring_guild_id TEXT NOT NULL,
                defending_guild_id TEXT NOT NULL,
                declaring_guild_wager INTEGER NOT NULL,
                defending_guild_wager INTEGER NOT NULL,
                total_pot INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                resolved_at TEXT,
                winner_guild_id TEXT,
                created_at_timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create war_wagers table", e)
        }
    }

    private fun preload() {
        val sql = "SELECT * FROM war_wagers"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val wager = mapResultSetToWarWager(result)
                wagers[wager.id] = wager
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload war wagers", e)
        }
    }

    private fun mapResultSetToWarWager(rs: co.aikar.idb.DbRow): WarWager {
        val id = UUID.fromString(rs.getString("id"))
        val warId = UUID.fromString(rs.getString("war_id"))
        val declaringGuildId = UUID.fromString(rs.getString("declaring_guild_id"))
        val defendingGuildId = UUID.fromString(rs.getString("defending_guild_id"))
        val declaringGuildWager = rs.getInt("declaring_guild_wager")
        val defendingGuildWager = rs.getInt("defending_guild_wager")
        val totalPot = rs.getInt("total_pot")
        val status = WagerStatus.valueOf(rs.getString("status").uppercase())
        val createdAt = Instant.parse(rs.getString("created_at"))
        val resolvedAt = rs.getString("resolved_at")?.let { Instant.parse(it) }
        val winnerGuildId = rs.getString("winner_guild_id")?.let { UUID.fromString(it) }

        return WarWager(
            id = id,
            warId = warId,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            declaringGuildWager = declaringGuildWager,
            defendingGuildWager = defendingGuildWager,
            totalPot = totalPot,
            status = status,
            createdAt = createdAt,
            resolvedAt = resolvedAt,
            winnerGuildId = winnerGuildId
        )
    }

    override fun add(wager: WarWager): Boolean {
        val sql = """
            INSERT INTO war_wagers (id, war_id, declaring_guild_id, defending_guild_id, declaring_guild_wager,
                                  defending_guild_wager, total_pot, status, created_at, resolved_at, winner_guild_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                wager.id.toString(),
                wager.warId.toString(),
                wager.declaringGuildId.toString(),
                wager.defendingGuildId.toString(),
                wager.declaringGuildWager,
                wager.defendingGuildWager,
                wager.totalPot,
                wager.status.name.lowercase(),
                wager.createdAt.toString(),
                wager.resolvedAt?.toString(),
                wager.winnerGuildId?.toString()
            )
            wagers[wager.id] = wager
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun update(wager: WarWager): Boolean {
        val sql = """
            UPDATE war_wagers SET war_id = ?, declaring_guild_id = ?, defending_guild_id = ?, declaring_guild_wager = ?,
                                defending_guild_wager = ?, total_pot = ?, status = ?, created_at = ?, resolved_at = ?, winner_guild_id = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                wager.warId.toString(),
                wager.declaringGuildId.toString(),
                wager.defendingGuildId.toString(),
                wager.declaringGuildWager,
                wager.defendingGuildWager,
                wager.totalPot,
                wager.status.name.lowercase(),
                wager.createdAt.toString(),
                wager.resolvedAt?.toString(),
                wager.winnerGuildId?.toString(),
                wager.id.toString()
            )
            if (rowsAffected > 0) {
                wagers[wager.id] = wager
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun remove(wagerId: UUID): Boolean {
        val sql = "DELETE FROM war_wagers WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, wagerId.toString())
            if (rowsAffected > 0) {
                wagers.remove(wagerId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getById(wagerId: UUID): WarWager? = wagers[wagerId]

    override fun getByWarId(warId: UUID): WarWager? = wagers.values.find { it.warId == warId }

    override fun getByGuild(guildId: UUID): List<WarWager> =
        wagers.values.filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }

    override fun getByStatus(status: WagerStatus): List<WarWager> =
        wagers.values.filter { it.status == status }

    override fun getAll(): List<WarWager> = wagers.values.toList()

    override fun getPendingWagersForGuild(guildId: UUID): List<WarWager> =
        wagers.values.filter {
            (it.declaringGuildId == guildId || it.defendingGuildId == guildId) &&
            it.status == WagerStatus.ESCROWED
        }
}
