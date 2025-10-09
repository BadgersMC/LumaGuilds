package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class WarRepositorySQLite(private val storage: SQLiteStorage) : WarRepository {

    private val wars: MutableMap<UUID, War> = mutableMapOf()
    private val warDeclarations: MutableMap<UUID, WarDeclaration> = mutableMapOf()
    private val warStats: MutableMap<UUID, WarStats> = mutableMapOf()

    init {
        createWarTables()
        preload()
    }

    private fun createWarTables() {
        // Wars table
        val warTableSql = """
            CREATE TABLE IF NOT EXISTS wars (
                id TEXT PRIMARY KEY,
                declaring_guild_id TEXT NOT NULL,
                defending_guild_id TEXT NOT NULL,
                declared_at TEXT NOT NULL,
                started_at TEXT,
                ended_at TEXT,
                duration_seconds INTEGER NOT NULL,
                status TEXT NOT NULL,
                winner_guild_id TEXT,
                loser_guild_id TEXT,
                peace_terms TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        // War declarations table
        val declarationTableSql = """
            CREATE TABLE IF NOT EXISTS war_declarations (
                id TEXT PRIMARY KEY,
                declaring_guild_id TEXT NOT NULL,
                defending_guild_id TEXT NOT NULL,
                proposed_duration_seconds INTEGER NOT NULL,
                terms TEXT,
                declared_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                accepted BOOLEAN DEFAULT FALSE,
                rejected BOOLEAN DEFAULT FALSE,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        // War objectives table
        val objectivesTableSql = """
            CREATE TABLE IF NOT EXISTS war_objectives (
                id TEXT PRIMARY KEY,
                war_id TEXT NOT NULL,
                type TEXT NOT NULL,
                target_value INTEGER NOT NULL,
                current_value INTEGER DEFAULT 0,
                description TEXT NOT NULL,
                completed BOOLEAN DEFAULT FALSE,
                completed_at TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        // War statistics table
        val statsTableSql = """
            CREATE TABLE IF NOT EXISTS war_stats (
                war_id TEXT PRIMARY KEY,
                declaring_guild_kills INTEGER DEFAULT 0,
                defending_guild_kills INTEGER DEFAULT 0,
                declaring_guild_deaths INTEGER DEFAULT 0,
                defending_guild_deaths INTEGER DEFAULT 0,
                claims_captured INTEGER DEFAULT 0,
                claims_lost INTEGER DEFAULT 0,
                resources_stolen INTEGER DEFAULT 0,
                last_updated TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        try {
            storage.connection.executeUpdate(warTableSql)
            storage.connection.executeUpdate(declarationTableSql)
            storage.connection.executeUpdate(objectivesTableSql)
            storage.connection.executeUpdate(statsTableSql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create war tables", e)
        }
    }

    private fun preload() {
        preloadWars()
        preloadWarDeclarations()
        preloadWarStats()
    }

    private fun preloadWars() {
        val sql = "SELECT * FROM wars"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val war = mapResultSetToWar(result)
                wars[war.id] = war
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload wars", e)
        }
    }

    private fun preloadWarDeclarations() {
        val sql = "SELECT * FROM war_declarations"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val declaration = mapResultSetToWarDeclaration(result)
                warDeclarations[declaration.id] = declaration
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload war declarations", e)
        }
    }

    private fun preloadWarStats() {
        val sql = "SELECT * FROM war_stats"

        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val stats = mapResultSetToWarStats(result)
                warStats[stats.warId] = stats
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload war stats", e)
        }
    }

    private fun mapResultSetToWar(rs: co.aikar.idb.DbRow): War {
        val id = UUID.fromString(rs.getString("id"))
        val declaringGuildId = UUID.fromString(rs.getString("declaring_guild_id"))
        val defendingGuildId = UUID.fromString(rs.getString("defending_guild_id"))
        val declaredAt = Instant.parse(rs.getString("declared_at"))
        val startedAt = rs.getString("started_at")?.let { Instant.parse(it) }
        val endedAt = rs.getString("ended_at")?.let { Instant.parse(it) }
        val durationSeconds = rs.getLong("duration_seconds")
        val status = WarStatus.valueOf(rs.getString("status").uppercase())
        val winner = rs.getString("winner_guild_id")?.let { UUID.fromString(it) }
        val loser = rs.getString("loser_guild_id")?.let { UUID.fromString(it) }
        val peaceTerms = rs.getString("peace_terms")

        return War(
            id = id,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            declaredAt = declaredAt,
            startedAt = startedAt,
            endedAt = endedAt,
            duration = java.time.Duration.ofSeconds(durationSeconds),
            status = status,
            winner = winner,
            loser = loser,
            peaceTerms = peaceTerms
        )
    }

    private fun mapResultSetToWarDeclaration(rs: co.aikar.idb.DbRow): WarDeclaration {
        val id = UUID.fromString(rs.getString("id"))
        val declaringGuildId = UUID.fromString(rs.getString("declaring_guild_id"))
        val defendingGuildId = UUID.fromString(rs.getString("defending_guild_id"))
        val proposedDurationSeconds = rs.getLong("proposed_duration_seconds")
        val terms = rs.getString("terms")
        val declaredAt = Instant.parse(rs.getString("declared_at"))
        val expiresAt = Instant.parse(rs.getString("expires_at"))
        val accepted = rs.getString("accepted").toBoolean()
        val rejected = rs.getString("rejected").toBoolean()

        return WarDeclaration(
            id = id,
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            proposedDuration = java.time.Duration.ofSeconds(proposedDurationSeconds),
            terms = terms,
            declaredAt = declaredAt,
            expiresAt = expiresAt,
            accepted = accepted,
            rejected = rejected
        )
    }

    private fun mapResultSetToWarStats(rs: co.aikar.idb.DbRow): WarStats {
        val warId = UUID.fromString(rs.getString("war_id"))
        val declaringGuildKills = rs.getInt("declaring_guild_kills")
        val defendingGuildKills = rs.getInt("defending_guild_kills")
        val declaringGuildDeaths = rs.getInt("declaring_guild_deaths")
        val defendingGuildDeaths = rs.getInt("defending_guild_deaths")
        val claimsCaptured = rs.getInt("claims_captured")
        val claimsLost = rs.getInt("claims_lost")
        val resourcesStolen = rs.getInt("resources_stolen")
        val lastUpdated = Instant.parse(rs.getString("last_updated"))

        return WarStats(
            warId = warId,
            declaringGuildKills = declaringGuildKills,
            defendingGuildKills = defendingGuildKills,
            declaringGuildDeaths = declaringGuildDeaths,
            defendingGuildDeaths = defendingGuildDeaths,
            claimsCaptured = claimsCaptured,
            claimsLost = claimsLost,
            resourcesStolen = resourcesStolen,
            lastUpdated = lastUpdated
        )
    }

    override fun add(war: War): Boolean {
        val sql = """
            INSERT INTO wars (id, declaring_guild_id, defending_guild_id, declared_at, started_at, ended_at,
                            duration_seconds, status, winner_guild_id, loser_guild_id, peace_terms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                war.id.toString(),
                war.declaringGuildId.toString(),
                war.defendingGuildId.toString(),
                war.declaredAt.toString(),
                war.startedAt?.toString(),
                war.endedAt?.toString(),
                war.duration.seconds,
                war.status.name.lowercase(),
                war.winner?.toString(),
                war.loser?.toString(),
                war.peaceTerms
            )
            wars[war.id] = war
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun update(war: War): Boolean {
        val sql = """
            UPDATE wars SET declaring_guild_id = ?, defending_guild_id = ?, declared_at = ?, started_at = ?,
                          ended_at = ?, duration_seconds = ?, status = ?, winner_guild_id = ?, loser_guild_id = ?, peace_terms = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                war.declaringGuildId.toString(),
                war.defendingGuildId.toString(),
                war.declaredAt.toString(),
                war.startedAt?.toString(),
                war.endedAt?.toString(),
                war.duration.seconds,
                war.status.name.lowercase(),
                war.winner?.toString(),
                war.loser?.toString(),
                war.peaceTerms,
                war.id.toString()
            )
            if (rowsAffected > 0) {
                wars[war.id] = war
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun remove(warId: UUID): Boolean {
        val sql = "DELETE FROM wars WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, warId.toString())
            if (rowsAffected > 0) {
                wars.remove(warId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getById(warId: UUID): War? = wars[warId]

    override fun getActiveWars(): List<War> = wars.values.filter { it.isActive }

    override fun getWarsForGuild(guildId: UUID): List<War> =
        wars.values.filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }

    override fun getCurrentWarBetweenGuilds(guildA: UUID, guildB: UUID): War? =
        wars.values.find {
            it.isActive &&
            ((it.declaringGuildId == guildA && it.defendingGuildId == guildB) ||
             (it.declaringGuildId == guildB && it.defendingGuildId == guildA))
        }

    override fun getWarHistory(guildId: UUID, limit: Int): List<War> =
        wars.values
            .filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }
            .sortedByDescending { it.declaredAt }
            .take(limit)

    override fun getAll(): List<War> = wars.values.toList()

    override fun addWarDeclaration(declaration: WarDeclaration): Boolean {
        val sql = """
            INSERT INTO war_declarations (id, declaring_guild_id, defending_guild_id, proposed_duration_seconds,
                                        terms, declared_at, expires_at, accepted, rejected)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                declaration.id.toString(),
                declaration.declaringGuildId.toString(),
                declaration.defendingGuildId.toString(),
                declaration.proposedDuration.seconds,
                declaration.terms,
                declaration.declaredAt.toString(),
                declaration.expiresAt.toString(),
                declaration.accepted,
                declaration.rejected
            )
            warDeclarations[declaration.id] = declaration
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun updateWarDeclaration(declaration: WarDeclaration): Boolean {
        val sql = """
            UPDATE war_declarations SET declaring_guild_id = ?, defending_guild_id = ?, proposed_duration_seconds = ?,
                                      terms = ?, declared_at = ?, expires_at = ?, accepted = ?, rejected = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                declaration.declaringGuildId.toString(),
                declaration.defendingGuildId.toString(),
                declaration.proposedDuration.seconds,
                declaration.terms,
                declaration.declaredAt.toString(),
                declaration.expiresAt.toString(),
                declaration.accepted,
                declaration.rejected,
                declaration.id.toString()
            )
            if (rowsAffected > 0) {
                warDeclarations[declaration.id] = declaration
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun removeWarDeclaration(declarationId: UUID): Boolean {
        val sql = "DELETE FROM war_declarations WHERE id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, declarationId.toString())
            if (rowsAffected > 0) {
                warDeclarations.remove(declarationId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getWarDeclarationById(declarationId: UUID): WarDeclaration? = warDeclarations[declarationId]

    override fun getPendingDeclarationsForGuild(guildId: UUID): List<WarDeclaration> =
        warDeclarations.values.filter { it.defendingGuildId == guildId && it.isValid }

    override fun getDeclarationsByGuild(guildId: UUID): List<WarDeclaration> =
        warDeclarations.values.filter { it.declaringGuildId == guildId }

    override fun getExpiredWarDeclarations(): List<WarDeclaration> =
        warDeclarations.values.filter { !it.isValid }

    override fun addWarStats(stats: WarStats): Boolean {
        val sql = """
            INSERT INTO war_stats (war_id, declaring_guild_kills, defending_guild_kills, declaring_guild_deaths,
                                 defending_guild_deaths, claims_captured, claims_lost, resources_stolen, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                stats.warId.toString(),
                stats.declaringGuildKills,
                stats.defendingGuildKills,
                stats.declaringGuildDeaths,
                stats.defendingGuildDeaths,
                stats.claimsCaptured,
                stats.claimsLost,
                stats.resourcesStolen,
                stats.lastUpdated.toString()
            )
            warStats[stats.warId] = stats
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun updateWarStats(stats: WarStats): Boolean {
        val sql = """
            UPDATE war_stats SET declaring_guild_kills = ?, defending_guild_kills = ?, declaring_guild_deaths = ?,
                               defending_guild_deaths = ?, claims_captured = ?, claims_lost = ?, resources_stolen = ?, last_updated = ?
            WHERE war_id = ?
        """.trimIndent()

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                stats.declaringGuildKills,
                stats.defendingGuildKills,
                stats.declaringGuildDeaths,
                stats.defendingGuildDeaths,
                stats.claimsCaptured,
                stats.claimsLost,
                stats.resourcesStolen,
                stats.lastUpdated.toString(),
                stats.warId.toString()
            )
            if (rowsAffected > 0) {
                warStats[stats.warId] = stats
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun getWarStatsByWarId(warId: UUID): WarStats? = warStats[warId]

    override fun getAllWarStats(): List<WarStats> = warStats.values.toList()

    override fun removeWarStats(warId: UUID): Boolean {
        val sql = "DELETE FROM war_stats WHERE war_id = ?"

        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, warId.toString())
            if (rowsAffected > 0) {
                warStats.remove(warId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
}
