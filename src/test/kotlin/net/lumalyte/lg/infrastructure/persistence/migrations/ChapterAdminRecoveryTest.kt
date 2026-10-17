package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class ChapterAdminRecoveryTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("recovery.db")}")
        ChapterLifecycleSchema.create(connection, mariaDb = false)
        connection.createStatement().use {
            it.execute("""INSERT INTO chapter_lifecycle
                (chapter_id,chapter_name,phase,starts_at,ends_at,last_error,transition_token,updated_at,version)
                VALUES ('chapter-2','Chapter 2','SCHEDULED',100,1000,NULL,NULL,100,0)""")
        }
    }

    @AfterEach
    fun tearDown() = connection.close()
    @Test
    fun `postpone moves only a scheduled chapter end forward`() {
        val admin = ChapterAdminRecoverySQL(connection)

        val status = admin.postpone("chapter-2", newEndAt = 2000, now = 500)

        assertEquals("SCHEDULED", status.phase)
        assertEquals(2000, status.endsAt)
        assertEquals(1, status.version)
        assertThrows(IllegalArgumentException::class.java) {
            admin.postpone("chapter-2", newEndAt = 400, now = 500)
        }
    }

    @Test
    fun `postpone rejects a future end that would shorten the current schedule`() {
        val admin = ChapterAdminRecoverySQL(connection)

        assertThrows(IllegalStateException::class.java) {
            admin.postpone("chapter-2", newEndAt = 600, now = 500)
        }

        assertEquals(1000, admin.status("chapter-2").endsAt)
        assertEquals(0, admin.status("chapter-2").version)
    }

    @Test
    fun `retry clears failure metadata without skipping lifecycle state`() {
        connection.createStatement().use {
            it.execute("""UPDATE chapter_lifecycle
                SET phase='FROZEN', last_error='backup failed', transition_token='abc'
                WHERE chapter_id='chapter-2'""")
        }
        val admin = ChapterAdminRecoverySQL(connection)

        val status = admin.retry("chapter-2", now = 600)

        assertEquals("FROZEN", status.phase)
        assertEquals(null, status.lastError)
        assertEquals(null, status.transitionToken)
        assertEquals(1, status.version)
    }
    @Test
    fun `force requires explicit confirmation and never skips backup states`() {
        val admin = ChapterAdminRecoverySQL(connection)

        assertThrows(IllegalArgumentException::class.java) {
            admin.forceDue("chapter-2", confirmation = "yes", now = 700)
        }

        val forced = admin.forceDue("chapter-2", confirmation = "CONFIRM", now = 700)
        assertEquals("SCHEDULED", forced.phase)
        assertEquals(700, forced.endsAt)

        connection.createStatement().use {
            it.execute("UPDATE chapter_lifecycle SET phase='FROZEN', last_error='x' WHERE chapter_id='chapter-2'")
        }
        val frozen = admin.forceDue("chapter-2", confirmation = "CONFIRM", now = 800)
        assertEquals("FROZEN", frozen.phase)
        assertEquals(null, frozen.lastError)
        assertFalse(frozen.phase == "BACKED_UP")
    }

    @Test
    fun `record failure persists durable recovery metadata`() {
        val admin = ChapterAdminRecoverySQL(connection)

        val status = admin.recordFailure(
            chapterId = "chapter-2",
            error = "backup exploded",
            transitionToken = "backup-42",
            now = 650,
        )

        assertEquals("backup exploded", status.lastError)
        assertEquals("backup-42", status.transitionToken)
        assertEquals(650, status.updatedAt)
        assertEquals(1, status.version)
    }

    @Test
    fun `status reports backup verification without mutating state`() {
        connection.createStatement().use {
            it.execute("""INSERT INTO chapter_backup_evidence
                (backup_id,chapter_id,storage_ref,sha256,size_bytes,created_at,verified_at,verification_status,restore_verified_at)
                VALUES ('b1','chapter-2','b1.db','abc',10,1,2,'VERIFIED',3)""")
            it.execute("UPDATE chapter_lifecycle SET phase='BACKED_UP', backup_id='b1' WHERE chapter_id='chapter-2'")
        }
        val admin = ChapterAdminRecoverySQL(connection)

        val status = admin.status("chapter-2")

        assertEquals("BACKED_UP", status.phase)
        assertTrue(status.backupVerified)
        assertTrue(status.restoreVerified)
        assertEquals(0, status.version)
    }
}
