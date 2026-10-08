package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class ExperienceAwardRepositorySQL(
    private val storage: Storage<Database>,
    private val curveProvider: () -> ProgressionCurve,
) : ExperienceAwardRepository {

    constructor(storage: Storage<Database>, curve: ProgressionCurve) : this(storage, { curve })

    private val mariaDb = storage.dialect == SqlDialect.MARIADB

    init {
        createTables()
    }

    override fun awardAtomically(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult {
        require(requestedXp > 0) { "Requested XP must be positive" }
        require(request.source == policy.source) { "Request source must match policy source" }
        require(policy.isCapped == (window != null)) { "Cap window must match policy period" }
        val curve = curveProvider()

        return storage.connection.connection.use { connection ->
            connection.committingTransaction {
                if (!mariaDb) {
                    // Reserve the SQLite writer before opening a SELECT snapshot.
                    execute(connection, "UPDATE guild_experience_source_usage SET awarded_xp = awarded_xp WHERE 0")
                }
                if (mariaDb) {
                    checkNotNull(
                        query(
                            connection,
                            "SELECT level FROM guilds WHERE id = ? FOR UPDATE",
                            request.guildId.toString(),
                        ) {
                            it.getInt("level")
                        },
                    ) {
                        "Guild ${request.guildId} does not exist"
                    }
                }
                ExperienceAwardTransaction(connection, mariaDb, curve).award(request, policy, requestedXp, window)
            }
        }
    }

    /** Caller holds the guild lock and commits caps, progression and its companion ledger together. */
    internal fun awardInTransaction(
        connection: Connection,
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult =
        ExperienceAwardTransaction(connection, mariaDb, curveProvider()).award(request, policy, requestedXp, window)

    override fun getAwardedXpByPool(guildId: UUID, at: java.time.Instant): Map<String, Int> {
        val sql =
            "SELECT source_pool, awarded_xp FROM guild_experience_source_usage " +
            "WHERE guild_id = ? AND period_start <= ? AND period_end > ?"
        return storage.connection.getResults(
            sql,
            guildId.toString(),
            at.toEpochMilli(),
            at.toEpochMilli(),
        ).associate { it.getString("source_pool") to it.getInt("awarded_xp") }
    }

    private fun execute(connection: Connection, sql: String, vararg parameters: Any?): Int {
        return connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun <T> query(connection: Connection, sql: String, vararg parameters: Any?, mapper: (ResultSet) -> T): T? {
        return connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { results -> if (results.next()) mapper(results) else null }
        }
    }

    private fun createTables() {
        createUsageTable()
        createProgressionTable()
        createReceiptTable()
    }

    private fun createUsageTable() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_experience_source_usage (
                guild_id VARCHAR(36) NOT NULL,
                source_pool VARCHAR(64) NOT NULL,
                period_start BIGINT NOT NULL,
                period_end BIGINT NOT NULL,
                awarded_xp INT NOT NULL DEFAULT 0,
                PRIMARY KEY (guild_id, source_pool, period_start)
            )
            """.trimIndent(),
        )
    }

    private fun createProgressionTable() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_progression (
                guild_id VARCHAR(36) PRIMARY KEY,
                total_experience INT NOT NULL DEFAULT 0,
                current_level INT NOT NULL DEFAULT 1,
                experience_this_level INT NOT NULL DEFAULT 0,
                experience_for_next_level INT NOT NULL DEFAULT 0,
                last_level_up BIGINT,
                total_level_ups INT NOT NULL DEFAULT 0,
                unlocked_perks TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                last_updated BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createReceiptTable() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS experience_transactions (
                id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                amount INT NOT NULL,
                source VARCHAR(64) NOT NULL,
                description TEXT,
                actor_id VARCHAR(36),
                timestamp BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }
}
