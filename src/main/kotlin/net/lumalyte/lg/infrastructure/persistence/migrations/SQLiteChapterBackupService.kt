package net.lumalyte.lg.infrastructure.persistence.migrations

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class ChapterBackupEvidence(
    val backupId: String,
    val chapterId: String,
    val storageRef: String,
    val sha256: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val verifiedAt: Long,
    val verificationStatus: String,
    val restoreVerifiedAt: Long?,
)

class SQLiteChapterBackupService(
    private val connection: Connection,
    private val backupDirectory: Path,
) {
    companion object {
        private val backupLocks = ConcurrentHashMap<String, ReentrantLock>()
    }
    fun createVerifiedBackup(
        chapterId: String,
        backupId: String,
        now: Long,
    ): ChapterBackupEvidence {
        require(chapterId.isNotBlank())
        require(backupId.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Backup id contains unsafe characters"
        }
        check(connection.autoCommit) {
            "SQLite chapter backup must run outside an active transaction"
        }

        val backupPath = backupDirectory.resolve("$backupId.db").toAbsolutePath().normalize()
        val lock = backupLocks.computeIfAbsent(backupPath.toString()) { ReentrantLock() }
        return lock.withLock {
            existingEvidence(backupId)?.let { evidence ->
                check(evidence.chapterId == chapterId) {
                    "Backup id already belongs to another chapter"
                }
                verifyExistingEvidence(evidence)
                return@withLock evidence
            }

            requireLifecyclePhase(chapterId, "FROZEN")
            Files.createDirectories(backupDirectory)
            if (Files.exists(backupPath)) {
                recoverOrphanedBackup(chapterId, backupId, backupPath, now)?.let {
                    return@withLock it
                }
            }
            try {
                createSnapshot(backupPath)
                val sha256 = sha256(backupPath)
                val sizeBytes = Files.size(backupPath)
                check(sizeBytes > 0) { "Backup file is empty" }
                verifyRestorableCopy(backupPath, backupId)

                val evidence = ChapterBackupEvidence(
                    backupId = backupId,
                    chapterId = chapterId,
                    storageRef = backupPath.toString(),
                    sha256 = sha256,
                    sizeBytes = sizeBytes,
                    createdAt = now,
                    verifiedAt = now,
                    verificationStatus = "VERIFIED",
                    restoreVerifiedAt = now,
                )
                persistVerifiedEvidence(evidence)
                evidence
            } catch (error: Exception) {
                try {
                    Files.deleteIfExists(backupPath)
                } catch (cleanup: Exception) {
                    error.addSuppressed(cleanup)
                }
                throw if (error is IllegalStateException || error is IllegalArgumentException) error
                else IllegalStateException("Failed to create verified SQLite chapter backup", error)
            }
        }
    }

    private fun recoverOrphanedBackup(
        chapterId: String,
        backupId: String,
        backupPath: Path,
        now: Long,
    ): ChapterBackupEvidence? {
        val evidence = try {
            check(Files.isRegularFile(backupPath)) { "Orphaned backup path is not a regular file" }
            val sizeBytes = Files.size(backupPath)
            check(sizeBytes > 0) { "Orphaned backup file is empty" }
            verifyRestorableCopy(backupPath, backupId)
            verifyOrphanChapterState(backupPath, chapterId)
            ChapterBackupEvidence(
                backupId = backupId,
                chapterId = chapterId,
                storageRef = backupPath.toString(),
                sha256 = sha256(backupPath),
                sizeBytes = sizeBytes,
                createdAt = now,
                verifiedAt = now,
                verificationStatus = "VERIFIED",
                restoreVerifiedAt = now,
            )
        } catch (_: Exception) {
            Files.deleteIfExists(backupPath)
            return null
        }

        // If the previous process crashed after the file was fully written but before
        // evidence committed, adopt the verified file instead of deadlocking retries.
        persistVerifiedEvidence(evidence)
        return evidence
    }

    private fun verifyOrphanChapterState(backupPath: Path, chapterId: String) {
        DriverManager.getConnection("jdbc:sqlite:$backupPath").use { restored ->
            val phase = restored.prepareStatement(
                "SELECT phase FROM chapter_lifecycle WHERE chapter_id=?"
            ).use { statement ->
                statement.setString(1, chapterId)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "Orphaned backup does not contain the expected chapter" }
                    rows.getString(1)
                }
            }
            check(phase == "FROZEN") {
                "Orphaned backup was not captured from the frozen chapter state"
            }
        }
    }

    private fun createSnapshot(backupPath: Path) {
        val escaped = backupPath.toString().replace("'", "''")
        connection.createStatement().use { statement ->
            statement.execute("VACUUM INTO '$escaped'")
        }
    }

    private fun verifyRestorableCopy(backupPath: Path, backupId: String) {
        val restorePath = backupDirectory.resolve(".$backupId.restore-verify.db")
        Files.deleteIfExists(restorePath)
        var verificationError: Exception? = null
        try {
            Files.copy(backupPath, restorePath, StandardCopyOption.REPLACE_EXISTING)
            DriverManager.getConnection("jdbc:sqlite:$restorePath").use { restored ->
                val integrity = restored.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA integrity_check").use { rows ->
                        check(rows.next()) { "Restore verification produced no integrity result" }
                        rows.getString(1)
                    }
                }
                check(integrity.equals("ok", ignoreCase = true)) {
                    "Restore verification integrity check failed: $integrity"
                }
                requireTable(restored, "guilds")
                requireTable(restored, "guild_progression")
                requireTable(restored, "chapter_lifecycle")
            }
        } catch (error: Exception) {
            verificationError = error
            throw error
        } finally {
            try {
                Files.deleteIfExists(restorePath)
            } catch (cleanup: Exception) {
                if (verificationError != null) {
                    verificationError.addSuppressed(cleanup)
                } else {
                    throw cleanup
                }
            }
        }
    }
    private fun requireTable(restored: Connection, table: String) {
        val exists = restored.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?"
        ).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { rows -> rows.next() }
        }
        check(exists) { "Restore verification missing required table: $table" }
    }

    private fun persistVerifiedEvidence(evidence: ChapterBackupEvidence) {
        val oldAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.prepareStatement("""
                INSERT INTO chapter_backup_evidence
                (backup_id, chapter_id, storage_ref, sha256, size_bytes,
                 created_at, verified_at, verification_status, restore_verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'VERIFIED', ?)
            """.trimIndent()).use { statement ->
                statement.setString(1, evidence.backupId)
                statement.setString(2, evidence.chapterId)
                statement.setString(3, evidence.storageRef)
                statement.setString(4, evidence.sha256)
                statement.setLong(5, evidence.sizeBytes)
                statement.setLong(6, evidence.createdAt)
                statement.setLong(7, evidence.verifiedAt)
                statement.setLong(8, requireNotNull(evidence.restoreVerifiedAt))
                check(statement.executeUpdate() == 1)
            }
            connection.prepareStatement("""
                UPDATE chapter_lifecycle
                SET phase='BACKED_UP', backup_id=?, updated_at=?, version=version+1, last_error=NULL
                WHERE chapter_id=? AND phase='FROZEN'
            """.trimIndent()).use { statement ->
                statement.setString(1, evidence.backupId)
                statement.setLong(2, evidence.verifiedAt)
                statement.setString(3, evidence.chapterId)
                check(statement.executeUpdate() == 1) {
                    "Chapter lifecycle changed before backup evidence could be committed"
                }
            }
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }
    private fun existingEvidence(backupId: String): ChapterBackupEvidence? =
        connection.prepareStatement("""
            SELECT backup_id, chapter_id, storage_ref, sha256, size_bytes,
                   created_at, verified_at, verification_status, restore_verified_at
            FROM chapter_backup_evidence
            WHERE backup_id=?
        """.trimIndent()).use { statement ->
            statement.setString(1, backupId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                ChapterBackupEvidence(
                    backupId = rows.getString("backup_id"),
                    chapterId = rows.getString("chapter_id"),
                    storageRef = rows.getString("storage_ref"),
                    sha256 = rows.getString("sha256"),
                    sizeBytes = rows.getLong("size_bytes"),
                    createdAt = rows.getLong("created_at"),
                    verifiedAt = rows.getLong("verified_at"),
                    verificationStatus = rows.getString("verification_status"),
                    restoreVerifiedAt = rows.getLong("restore_verified_at").let {
                        if (rows.wasNull()) null else it
                    },
                )
            }
        }

    private fun verifyExistingEvidence(evidence: ChapterBackupEvidence) {
        check(evidence.verificationStatus == "VERIFIED" && evidence.restoreVerifiedAt != null) {
            "Existing backup evidence is not restore verified"
        }
        val path = Path.of(evidence.storageRef)
        check(Files.isRegularFile(path)) { "Recorded backup file is missing" }
        check(Files.size(path) == evidence.sizeBytes) { "Recorded backup size changed" }
        check(sha256(path) == evidence.sha256) { "Recorded backup checksum changed" }
        verifyRestorableCopy(path, evidence.backupId)
    }

    private fun requireLifecyclePhase(chapterId: String, expected: String) {
        val phase = connection.prepareStatement(
            "SELECT phase FROM chapter_lifecycle WHERE chapter_id=?"
        ).use { statement ->
            statement.setString(1, chapterId)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Chapter lifecycle row is missing" }
                rows.getString(1)
            }
        }
        check(phase == expected) {
            "Chapter backup requires phase=$expected; current phase=$phase"
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
