package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

data class ChapterAdminStatus(
    val chapterId: String,
    val chapterName: String,
    val phase: String,
    val startsAt: Long?,
    val endsAt: Long?,
    val backupId: String?,
    val backupVerified: Boolean,
    val restoreVerified: Boolean,
    val lastError: String?,
    val transitionToken: String?,
    val updatedAt: Long,
    val version: Int,
)

class ChapterAdminRecoverySQL(
    private val connection: Connection,
) {
    fun status(chapterId: String): ChapterAdminStatus {
        require(chapterId.isNotBlank())
        return connection.prepareStatement("""
            SELECT l.chapter_id, l.chapter_name, l.phase, l.starts_at, l.ends_at,
                   l.backup_id, l.last_error, l.transition_token, l.updated_at, l.version,
                   b.verification_status, b.restore_verified_at
            FROM chapter_lifecycle l
            LEFT JOIN chapter_backup_evidence b ON b.backup_id = l.backup_id
            WHERE l.chapter_id = ?
        """.trimIndent()).use { statement ->
            statement.setString(1, chapterId)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Chapter lifecycle row is missing" }
                val startsAt = rows.getLong("starts_at").let { if (rows.wasNull()) null else it }
                val endsAt = rows.getLong("ends_at").let { if (rows.wasNull()) null else it }
                val restoreVerifiedAt = rows.getLong("restore_verified_at").let { if (rows.wasNull()) null else it }
                val verificationStatus = rows.getString("verification_status")
                ChapterAdminStatus(
                    chapterId = rows.getString("chapter_id"),
                    chapterName = rows.getString("chapter_name"),
                    phase = rows.getString("phase"),
                    startsAt = startsAt,
                    endsAt = endsAt,
                    backupId = rows.getString("backup_id"),
                    backupVerified = verificationStatus == "VERIFIED",
                    restoreVerified = verificationStatus == "VERIFIED" && restoreVerifiedAt != null,
                    lastError = rows.getString("last_error"),
                    transitionToken = rows.getString("transition_token"),
                    updatedAt = rows.getLong("updated_at"),
                    version = rows.getInt("version"),
                )
            }
        }
    }

    fun postpone(chapterId: String, newEndAt: Long, now: Long): ChapterAdminStatus {
        require(newEndAt > now) { "Postponed chapter end must be in the future" }
        update(
            """
            UPDATE chapter_lifecycle
            SET ends_at=?, updated_at=?, version=version+1,
                last_error=NULL, transition_token=NULL
            WHERE chapter_id=? AND phase='SCHEDULED'
              AND (ends_at IS NULL OR ends_at < ?)
            """.trimIndent(),
            newEndAt, now, chapterId, newEndAt,
            failure = "Only a scheduled chapter can be postponed to a later end time",
        )
        return status(chapterId)
    }

    fun retry(chapterId: String, now: Long): ChapterAdminStatus {
        update(
            """
            UPDATE chapter_lifecycle
            SET last_error=NULL, transition_token=NULL,
                updated_at=?, version=version+1
            WHERE chapter_id=? AND phase <> 'COMPLETE'
            """.trimIndent(),
            now, chapterId,
            failure = "Completed chapter cannot be retried",
        )
        return status(chapterId)
    }

    fun forceDue(chapterId: String, confirmation: String, now: Long): ChapterAdminStatus {
        require(confirmation == "CONFIRM") { "Force requires literal CONFIRM" }
        val current = status(chapterId)
        check(current.phase != "COMPLETE") { "Completed chapter cannot be forced" }

        if (current.phase == "SCHEDULED") {
            update(
                """
                UPDATE chapter_lifecycle
                SET ends_at=?, last_error=NULL, transition_token=NULL,
                    updated_at=?, version=version+1
                WHERE chapter_id=? AND phase='SCHEDULED'
                """.trimIndent(),
                now, now, chapterId,
                failure = "Scheduled chapter changed before force could be applied",
            )
        } else {
            update(
                """
                UPDATE chapter_lifecycle
                SET last_error=NULL, transition_token=NULL,
                    updated_at=?, version=version+1
                WHERE chapter_id=? AND phase <> 'COMPLETE'
                """.trimIndent(),
                now, chapterId,
                failure = "Chapter cannot be forced from its current state",
            )
        }
        return status(chapterId)
    }

    fun recordFailure(chapterId: String, error: String, transitionToken: String?, now: Long): ChapterAdminStatus {
        require(error.isNotBlank())
        update(
            """
            UPDATE chapter_lifecycle
            SET last_error=?, transition_token=?, updated_at=?, version=version+1
            WHERE chapter_id=? AND phase <> 'COMPLETE'
            """.trimIndent(),
            error, transitionToken, now, chapterId,
            failure = "Chapter cannot record a failure in its current state",
        )
        return status(chapterId)
    }

    private fun update(sql: String, vararg params: Any?, failure: String) {
        connection.prepareStatement(sql).use { statement ->
            params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            check(statement.executeUpdate() == 1) { failure }
        }
    }
}
