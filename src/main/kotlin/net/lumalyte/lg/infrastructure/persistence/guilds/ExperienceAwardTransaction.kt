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
            ) { true } ?: false
        return if (duplicate) ExperienceAwardResult.Duplicate else awardNew(request, policy, requestedXp, window)
    }

    private fun awardNew(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult {
        val used = usage(request, policy, window)
        val accepted = acceptedXp(window, requestedXp, policy.capXp, used)
        return if (accepted == 0) {
            ExperienceAwardResult.NoAllowance(policy.capXp, used)
        } else {
            reserve(request, policy, window, accepted)
            val level = advance(request, accepted)
            record(request, accepted)
            ExperienceAwardResult.Awarded(accepted, used + accepted, policy.isCapped, leveledUpTo = level)
        }
    }

    private fun acceptedXp(window: PeriodWindow?, requested: Int, cap: Int, used: Int): Int =
        if (window == null) requested else requested.coerceAtMost((cap - used).coerceAtLeast(0))

    private fun usage(request: ExperienceAwardRequest, policy: ExperiencePolicy, window: PeriodWindow?): Int {
        if (window == null) return 0
        connection.updateStatement(
            usageSeedSql(mariaDb),
            request.guildId.toString(),
            policy.pool,
            window.startInclusive.toEpochMilli(),
            window.endExclusive.toEpochMilli(),
        )
        return readUsage(request, policy, window)
    }

    private fun readUsage(request: ExperienceAwardRequest, policy: ExperiencePolicy, window: PeriodWindow): Int {
        val locking = if (mariaDb) " FOR UPDATE" else ""
        val sql =
            "SELECT awarded_xp FROM guild_experience_source_usage " +
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
        val updated =
            connection.updateStatement(
                RESERVE_EXPERIENCE_SQL,
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
            progressionUpsertSql(mariaDb), request.guildId.toString(), total, level,
            curve.experienceInCurrentLevel(total), curve.experienceForNextLevel(level), lastLevelUp,
            levelUps, "[]", existing.createdAt, request.occurredAt.toEpochMilli(),
        )
        connection.updateStatement("UPDATE guilds SET level = ? WHERE id = ?", level, request.guildId.toString())
        return level.takeIf { changed }
    }

    private fun readProgression(request: ExperienceAwardRequest): ExistingProgression {
        val sql =
            "SELECT total_experience, current_level, total_level_ups, created_at " +
                "FROM guild_progression WHERE guild_id = ?"
        return connection.selectOne(sql, request.guildId.toString(), mapper = ::mapProgression)
            ?: ExistingProgression(0, 1, 0, request.occurredAt.toEpochMilli())
    }

    private fun mapProgression(row: java.sql.ResultSet): ExistingProgression {
        return ExistingProgression(
            row.getInt("total_experience"),
            row.getInt("current_level"),
            row.getInt("total_level_ups"),
            row.getLong("created_at"),
        )
    }

    private fun record(request: ExperienceAwardRequest, accepted: Int) {
        val sql =
            "INSERT INTO experience_transactions " +
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
}
