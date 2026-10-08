package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.domain.values.ProgressionCurve
import java.sql.Connection

/** One caller-owned transaction for cap reservation, progression and an idempotent XP receipt. */
internal class ExperienceAwardTransaction(
    private val connection: Connection,
    private val mariaDb: Boolean,
    private val curve: ProgressionCurve,
) {
    fun award(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult {
        val duplicate =
            connection.selectOne(
                "SELECT 1 FROM experience_transactions WHERE id = ?",
                request.transactionId.toString(),
            ) {
                true
            }
                ?: false
        if (duplicate) return ExperienceAwardResult.Duplicate
        val used = usage(request, policy, window)
        val accepted = if (window ==
            null
        ) {
            requestedXp
        } else {
            requestedXp.coerceAtMost((policy.capXp - used).coerceAtLeast(0))
        }
        return if (accepted == 0) {
            ExperienceAwardResult.NoAllowance(policy.capXp, used)
        } else {
            reserve(request, policy, window, accepted)
            val level = advance(request, accepted)
            record(request, accepted)
            ExperienceAwardResult.Awarded(accepted, used + accepted, policy.isCapped, leveledUpTo = level)
        }
    }

    private fun usage(request: ExperienceAwardRequest, policy: ExperiencePolicy, window: PeriodWindow?): Int {
        if (window == null) return 0
        connection.updateStatement(
            usageSeedSql(),
            request.guildId.toString(),
            policy.pool,
            window.startInclusive.toEpochMilli(),
            window.endExclusive.toEpochMilli(),
        )
        val locking = if (mariaDb) " FOR UPDATE" else ""
        val sql = "SELECT awarded_xp FROM guild_experience_source_usage " +
            "WHERE guild_id = ? AND source_pool = ? AND period_start = ?$locking"
        return connection.selectOne(
            sql,
            request.guildId.toString(),
            policy.pool,
            window.startInclusive.toEpochMilli(),
        ) {
            it.getInt("awarded_xp")
        } ?: 0
    }

    private fun reserve(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        window: PeriodWindow?,
        accepted: Int,
    ) {
        if (window == null) return
        val sql = "UPDATE guild_experience_source_usage SET period_end = ?, awarded_xp = awarded_xp + ? " +
            "WHERE guild_id = ? AND source_pool = ? AND period_start = ? AND awarded_xp + ? <= ?"
        val updated = connection.updateStatement(
            sql,
            window.endExclusive.toEpochMilli(),
            accepted,
            request.guildId.toString(),
            policy.pool,
            window.startInclusive.toEpochMilli(),
            accepted,
            policy.capXp,
        )
        check(updated == 1) { "Source cap reservation lost its row lock" }
    }

    private fun advance(request: ExperienceAwardRequest, accepted: Int): Int? {
        val existing = readProgression(request)
        val total = Math.addExact(existing.totalExperience, accepted)
        val level = curve.levelFromExperience(total)
        val changed = level > existing.currentLevel
        val lastLevelUp = if (changed) request.occurredAt.toEpochMilli() else null
        val levelUps = existing.totalLevelUps + (level - existing.currentLevel).coerceAtLeast(0)
        connection.updateStatement(
            progressionUpsertSql(), request.guildId.toString(), total, level,
            curve.experienceInCurrentLevel(total), curve.experienceForNextLevel(level), lastLevelUp,
            levelUps, "[]", existing.createdAt, request.occurredAt.toEpochMilli(),
        )
        connection.updateStatement("UPDATE guilds SET level = ? WHERE id = ?", level, request.guildId.toString())
        return level.takeIf { changed }
    }

    private fun readProgression(request: ExperienceAwardRequest): ExistingProgression {
        val sql = "SELECT total_experience, current_level, total_level_ups, created_at " +
            "FROM guild_progression WHERE guild_id = ?"
        return connection.selectOne(sql, request.guildId.toString()) { row ->
            ExistingProgression(
                row.getInt("total_experience"),
                row.getInt("current_level"),
                row.getInt("total_level_ups"),
                row.getLong("created_at"),
            )
        } ?: ExistingProgression(0, 1, 0, request.occurredAt.toEpochMilli())
    }

    private fun record(request: ExperienceAwardRequest, accepted: Int) {
        val sql = "INSERT INTO experience_transactions " +
            "(id, guild_id, amount, source, description, actor_id, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)"
        connection.updateStatement(
            sql,
            request.transactionId.toString(),
            request.guildId.toString(),
            accepted,
            request.source.name,
            "XP from ${request.source.name}",
            request.actorId?.toString(),
            request.occurredAt.toEpochMilli(),
        )
    }

    private data class ExistingProgression(
        val totalExperience: Int,
        val currentLevel: Int,
        val totalLevelUps: Int,
        val createdAt: Long,
    )

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
