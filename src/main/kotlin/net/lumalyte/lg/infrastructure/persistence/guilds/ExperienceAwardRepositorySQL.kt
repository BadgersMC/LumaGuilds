package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class ExperienceAwardRepositorySQL(
    private val storage: Storage<Database>,
    private val curveProvider: () -> ProgressionCurve,
) : ExperienceAwardRepository {

    constructor(storage: Storage<Database>, curve: ProgressionCurve) : this(storage, { curve })

    private val mariaDb = storage.javaClass.simpleName.contains("MariaDB")

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

        storage.connection.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
            if (mariaDb) {
                checkNotNull(query(connection,
                    "SELECT level FROM guilds WHERE id = ? FOR UPDATE",
                    request.guildId.toString(),
                ) { it.getInt("level") }) { "Guild ${request.guildId} does not exist" }
            }
            val usedXp = if (window == null) {
                0
            } else {
                execute(connection, usageSeedSql(),
                    request.guildId.toString(), policy.pool,
                    window.startInclusive.toEpochMilli(), window.endExclusive.toEpochMilli(),
                )
                query(connection,
                    "SELECT awarded_xp FROM guild_experience_source_usage WHERE guild_id = ? AND source_pool = ? AND period_start = ?${if (mariaDb) " FOR UPDATE" else ""}",
                    request.guildId.toString(),
                    policy.pool,
                    window.startInclusive.toEpochMilli(),
                ) { it.getInt("awarded_xp") } ?: 0
            }

            val acceptedXp = if (window == null) {
                requestedXp
            } else {
                requestedXp.coerceAtMost((policy.capXp - usedXp).coerceAtLeast(0))
            }
            if (acceptedXp == 0) {
                connection.commit()
                return ExperienceAwardResult.NoAllowance(policy.capXp, usedXp)
            }

            val totalUsedXp = usedXp + acceptedXp
            if (window != null) {
                val updated = execute(connection,
                    "UPDATE guild_experience_source_usage SET period_end = ?, awarded_xp = awarded_xp + ? WHERE guild_id = ? AND source_pool = ? AND period_start = ? AND awarded_xp + ? <= ?",
                    window.endExclusive.toEpochMilli(), acceptedXp, request.guildId.toString(), policy.pool,
                    window.startInclusive.toEpochMilli(), acceptedXp, policy.capXp,
                )
                check(updated == 1) { "Source cap reservation lost its row lock" }
            }

            val existing = query(connection,
                "SELECT total_experience, current_level, total_level_ups, created_at FROM guild_progression WHERE guild_id = ?",
                request.guildId.toString(),
            ) { row ->
                ExistingProgression(
                    row.getInt("total_experience"),
                    row.getInt("current_level"),
                    row.getInt("total_level_ups"),
                    row.getLong("created_at"),
                )
            }
            val oldTotalXp = existing?.totalExperience ?: 0
            val oldLevel = existing?.currentLevel ?: 1
            val oldLevelUps = existing?.totalLevelUps ?: 0
            val createdAt = existing?.createdAt ?: request.occurredAt.toEpochMilli()
            val newTotalXp = Math.addExact(oldTotalXp, acceptedXp)
            val newLevel = curve.levelFromExperience(newTotalXp)
            val levelUps = oldLevelUps + (newLevel - oldLevel).coerceAtLeast(0)
            val lastLevelUp = if (newLevel > oldLevel) request.occurredAt.toEpochMilli() else null

            execute(connection,
                progressionUpsertSql(),
                request.guildId.toString(),
                newTotalXp,
                newLevel,
                curve.experienceInCurrentLevel(newTotalXp),
                curve.experienceForNextLevel(newLevel),
                lastLevelUp,
                levelUps,
                "[]",
                createdAt,
                request.occurredAt.toEpochMilli(),
            )
            execute(connection,
                "UPDATE guilds SET level = ? WHERE id = ?",
                newLevel,
                request.guildId.toString(),
            )
            execute(connection,
                "INSERT INTO experience_transactions (id, guild_id, amount, source, description, actor_id, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                request.guildId.toString(),
                acceptedXp,
                request.source.name,
                "XP from ${request.source.name}",
                request.actorId?.toString(),
                request.occurredAt.toEpochMilli(),
            )

                connection.commit()
                return ExperienceAwardResult.Awarded(
                    acceptedXp,
                    totalUsedXp,
                    policy.isCapped,
                    leveledUpTo = newLevel.takeIf { it > oldLevel },
                )
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun execute(connection: Connection, sql: String, vararg parameters: Any?): Int =
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }

    private fun <T> query(
        connection: Connection,
        sql: String,
        vararg parameters: Any?,
        mapper: (ResultSet) -> T,
    ): T? = connection.prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { results -> if (results.next()) mapper(results) else null }
    }

    private data class ExistingProgression(
        val totalExperience: Int,
        val currentLevel: Int,
        val totalLevelUps: Int,
        val createdAt: Long,
    )

    private fun createTables() {
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
            """.trimIndent()
        )
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
            """.trimIndent()
        )
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
            """.trimIndent()
        )
    }

    private fun usageSeedSql(): String = if (mariaDb) {
        """
        INSERT INTO guild_experience_source_usage
            (guild_id, source_pool, period_start, period_end, awarded_xp)
        VALUES (?, ?, ?, ?, 0)
        ON DUPLICATE KEY UPDATE period_end = VALUES(period_end)
        """.trimIndent()
    } else {
        """
        INSERT INTO guild_experience_source_usage
            (guild_id, source_pool, period_start, period_end, awarded_xp)
        VALUES (?, ?, ?, ?, 0)
        ON CONFLICT(guild_id, source_pool, period_start)
        DO UPDATE SET period_end = excluded.period_end
        """.trimIndent()
    }

    private fun progressionUpsertSql(): String = if (mariaDb) {
        """
        INSERT INTO guild_progression
            (guild_id, total_experience, current_level, experience_this_level,
             experience_for_next_level, last_level_up, total_level_ups,
             unlocked_perks, created_at, last_updated)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            total_experience = VALUES(total_experience),
            current_level = VALUES(current_level),
            experience_this_level = VALUES(experience_this_level),
            experience_for_next_level = VALUES(experience_for_next_level),
            last_level_up = COALESCE(VALUES(last_level_up), last_level_up),
            total_level_ups = VALUES(total_level_ups),
            last_updated = VALUES(last_updated)
        """.trimIndent()
    } else {
        """
        INSERT INTO guild_progression
            (guild_id, total_experience, current_level, experience_this_level,
             experience_for_next_level, last_level_up, total_level_ups,
             unlocked_perks, created_at, last_updated)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(guild_id) DO UPDATE SET
            total_experience = excluded.total_experience,
            current_level = excluded.current_level,
            experience_this_level = excluded.experience_this_level,
            experience_for_next_level = excluded.experience_for_next_level,
            last_level_up = COALESCE(excluded.last_level_up, guild_progression.last_level_up),
            total_level_ups = excluded.total_level_ups,
            last_updated = excluded.last_updated
        """.trimIndent()
    }
}
