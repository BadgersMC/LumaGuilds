package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

data class ChapterRolloverPlan(
    val currentChapterId: String,
    val currentChapterName: String,
    val nextChapterId: String,
    val nextChapterName: String,
    val nextStartsAt: Long,
    val nextEndsAt: Long,
)

class ChapterRolloverCoordinatorSQL(
    private val connection: Connection,
    private val verifiedBackup: (chapterId: String, backupId: String, now: Long) -> Unit,
) {
    fun advance(plan: ChapterRolloverPlan, now: Long): ChapterAdminStatus {
        val admin = ChapterAdminRecoverySQL(connection)
        val status = admin.status(plan.currentChapterId)
        return try {
            when (status.phase) {
                "SCHEDULED" -> {
                    if ((status.endsAt ?: Long.MAX_VALUE) > now) status
                    else updatePhase(plan.currentChapterId, "SCHEDULED", "FROZEN", now)
                }
                "FROZEN" -> {
                    val backupId = status.backupId ?: "${plan.currentChapterId}-rollover-${status.version + 1}"
                    verifiedBackup(plan.currentChapterId, backupId, now)
                    admin.status(plan.currentChapterId)
                }
                "BACKED_UP" -> {
                    requireVerifiedBackup(plan.currentChapterId)
                    archiveStandings(plan.currentChapterId, now)
                    updatePhase(plan.currentChapterId, "BACKED_UP", "ARCHIVED", now)
                }
                "ARCHIVED" -> {
                    initializeNextRatings(plan.nextChapterId, now)
                    updatePhase(plan.currentChapterId, "ARCHIVED", "RESET", now)
                }
                "RESET" -> {
                    pruneSeasonal(plan.currentChapterId)
                    updatePhase(plan.currentChapterId, "RESET", "PRUNED", now)
                }
                "PRUNED" -> {
                    completeAndScheduleNext(plan, now)
                    admin.status(plan.currentChapterId)
                }
                "COMPLETE" -> status
                else -> error("Unknown chapter phase: ${status.phase}")
            }
        } catch (error: Exception) {
            if (status.phase != "COMPLETE") {
                runCatching {
                    admin.recordFailure(
                        plan.currentChapterId,
                        error.message ?: error.javaClass.simpleName,
                        status.phase,
                        now,
                    )
                }
            }
            admin.status(plan.currentChapterId)
        }
    }

    fun catchUp(plan: ChapterRolloverPlan, now: Long): ChapterAdminStatus {
        var status = ChapterAdminRecoverySQL(connection).status(plan.currentChapterId)
        repeat(8) {
            val next = advance(plan, now)
            if (next.phase == status.phase && next.version == status.version) return next
            status = next
            if (status.phase == "COMPLETE" || status.lastError != null) return status
        }
        return status
    }

    private fun archiveStandings(chapterId: String, now: Long) {
        connection.prepareStatement("""
            INSERT INTO chapter_standings_archive
            (chapter_id,guild_id,placement,run_level,run_experience,seasonal_elo,canonical_gold,archived_at)
            SELECT ?, g.id,
                   ROW_NUMBER() OVER (ORDER BY COALESCE(r.elo,1000) DESC, p.current_level DESC, p.total_experience DESC, LOWER(g.name), g.id),
                   p.current_level, p.total_experience, COALESCE(r.elo,1000), COALESCE(v.balance,0), ?
            FROM guilds g
            JOIN guild_progression p ON p.guild_id=g.id
            LEFT JOIN chapter_seasonal_ratings r ON r.chapter_id=? AND r.guild_id=g.id
            LEFT JOIN vault_gold v ON v.guild_id=g.id
        """.trimIndent()).use { statement ->
            statement.setString(1, chapterId)
            statement.setLong(2, now)
            statement.setString(3, chapterId)
            statement.executeUpdate()
        }
    }

    private fun initializeNextRatings(nextChapterId: String, now: Long) {
        connection.prepareStatement("""
            INSERT INTO chapter_seasonal_ratings (chapter_id,guild_id,elo,updated_at)
            SELECT ?, id, 1000, ? FROM guilds
        """.trimIndent()).use { statement ->
            statement.setString(1, nextChapterId)
            statement.setLong(2, now)
            statement.executeUpdate()
        }
    }

    private fun pruneSeasonal(chapterId: String) {
        connection.prepareStatement("DELETE FROM chapter_seasonal_ratings WHERE chapter_id=?").use {
            it.setString(1, chapterId)
            it.executeUpdate()
        }
        if (tableExists("chapter_rated_pair_guards")) {
            connection.prepareStatement("DELETE FROM chapter_rated_pair_guards WHERE chapter_id=?").use {
                it.setString(1, chapterId)
                it.executeUpdate()
            }
        }
    }

    private fun completeAndScheduleNext(plan: ChapterRolloverPlan, now: Long) {
        val oldAuto = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.prepareStatement("""
                UPDATE chapter_lifecycle SET phase='COMPLETE', updated_at=?, version=version+1,
                    last_error=NULL, transition_token=NULL
                WHERE chapter_id=? AND phase='PRUNED'
            """.trimIndent()).use {
                it.setLong(1, now)
                it.setString(2, plan.currentChapterId)
                check(it.executeUpdate() == 1)
            }
            connection.prepareStatement("""
                INSERT INTO chapter_lifecycle
                (chapter_id,chapter_name,phase,starts_at,ends_at,updated_at,version)
                VALUES (?,?,'SCHEDULED',?,?,?,0)
            """.trimIndent()).use {
                it.setString(1, plan.nextChapterId)
                it.setString(2, plan.nextChapterName)
                it.setLong(3, plan.nextStartsAt)
                it.setLong(4, plan.nextEndsAt)
                it.setLong(5, now)
                it.executeUpdate()
            }
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = oldAuto
        }
    }

    private fun requireVerifiedBackup(chapterId: String) {
        val ok = connection.prepareStatement("""
            SELECT 1 FROM chapter_lifecycle l
            JOIN chapter_backup_evidence b ON b.backup_id=l.backup_id
            WHERE l.chapter_id=? AND b.verification_status='VERIFIED'
              AND b.verified_at IS NOT NULL AND b.restore_verified_at IS NOT NULL
        """.trimIndent()).use {
            it.setString(1, chapterId)
            it.executeQuery().use { rows -> rows.next() }
        }
        check(ok) { "Verified restorable backup required before archive" }
    }

    private fun updatePhase(chapterId: String, from: String, to: String, now: Long): ChapterAdminStatus {
        connection.prepareStatement("""
            UPDATE chapter_lifecycle SET phase=?,updated_at=?,version=version+1,last_error=NULL,transition_token=NULL
            WHERE chapter_id=? AND phase=?
        """.trimIndent()).use {
            it.setString(1, to)
            it.setLong(2, now)
            it.setString(3, chapterId)
            it.setString(4, from)
            check(it.executeUpdate() == 1) { "Chapter phase changed concurrently" }
        }
        return ChapterAdminRecoverySQL(connection).status(chapterId)
    }

    private fun tableExists(name: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use {
            it.setString(1, name)
            it.executeQuery().use { rows -> rows.next() }
        }
}
