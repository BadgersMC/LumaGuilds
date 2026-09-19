package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChapterBackupServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection
    private lateinit var databaseFile: Path

    @BeforeEach
    fun setUp() {
        databaseFile = tempDir.resolve("lumaguilds.db")
        connection = DriverManager.getConnection("jdbc:sqlite:$databaseFile")
        connection.createStatement().use { s ->
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT NOT NULL)")
            s.execute("CREATE TABLE guild_progression (guild_id TEXT PRIMARY KEY, total_experience INTEGER NOT NULL)")
        }
        ChapterLifecycleSchema.create(connection, mariaDb = false)
        connection.createStatement().use { s ->
            s.execute("INSERT INTO guilds VALUES ('g1','Alpha')")
            s.execute("INSERT INTO guild_progression VALUES ('g1',12345)")
            s.execute("""INSERT INTO chapter_lifecycle
                (chapter_id,chapter_name,phase,updated_at,version)
                VALUES ('chapter-1','Chapter 1','FROZEN',1,0)""")
        }
    }
    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `backup captures committed WAL state and becomes restore verified`() {
        val service = SQLiteChapterBackupService(connection, tempDir.resolve("backups"))

        val evidence = service.createVerifiedBackup("chapter-1", "cutover-backup", 1000)

        assertTrue(Files.exists(Path.of(evidence.storageRef)))
        assertTrue(evidence.sizeBytes > 0)
        assertEquals(64, evidence.sha256.length)
        assertEquals("VERIFIED", evidence.verificationStatus)
        assertTrue(evidence.restoreVerifiedAt != null)
        assertEquals("BACKED_UP", scalarString(
            "SELECT phase FROM chapter_lifecycle WHERE chapter_id='chapter-1'"
        ))
        assertEquals("cutover-backup", scalarString(
            "SELECT backup_id FROM chapter_lifecycle WHERE chapter_id='chapter-1'"
        ))
        assertEquals("VERIFIED", scalarString(
            "SELECT verification_status FROM chapter_backup_evidence WHERE backup_id='cutover-backup'"
        ))

        DriverManager.getConnection("jdbc:sqlite:${Path.of(evidence.storageRef)}").use { restored ->
            restored.createStatement().use { s ->
                s.executeQuery("SELECT total_experience FROM guild_progression WHERE guild_id='g1'").use { r ->
                    assertTrue(r.next())
                    assertEquals(12345, r.getInt(1))
                }
            }
        }
    }
    @Test
    fun `backup refuses to run unless chapter is frozen`() {
        connection.createStatement().use {
            it.execute("UPDATE chapter_lifecycle SET phase='SCHEDULED' WHERE chapter_id='chapter-1'")
        }
        val service = SQLiteChapterBackupService(connection, tempDir.resolve("backups"))

        assertThrows(IllegalStateException::class.java) {
            service.createVerifiedBackup("chapter-1", "cutover-backup", 1000)
        }

        assertEquals(0, scalarInt("SELECT COUNT(*) FROM chapter_backup_evidence"))
        assertFalse(Files.exists(tempDir.resolve("backups").resolve("cutover-backup.db")))
    }

    @Test
    fun `existing backup id is idempotent only when evidence and file still verify`() {
        val service = SQLiteChapterBackupService(connection, tempDir.resolve("backups"))
        val first = service.createVerifiedBackup("chapter-1", "cutover-backup", 1000)
        val second = service.createVerifiedBackup("chapter-1", "cutover-backup", 2000)

        assertEquals(first.sha256, second.sha256)
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM chapter_backup_evidence"))
    }

    @Test
    fun `concurrent service instances serialize the same backup id`() {
        val backups = tempDir.resolve("backups")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (1..2).map { index ->
                executor.submit<ChapterBackupEvidence> {
                    DriverManager.getConnection("jdbc:sqlite:$databaseFile").use { worker ->
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        SQLiteChapterBackupService(worker, backups)
                            .createVerifiedBackup("chapter-1", "shared-backup", 1000L + index)
                    }
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            val results = futures.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(results[0].sha256, results[1].sha256)
            assertTrue(Files.isRegularFile(backups.resolve("shared-backup.db")))
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM chapter_backup_evidence WHERE backup_id='shared-backup'"))
            assertEquals("BACKED_UP", scalarString(
                "SELECT phase FROM chapter_lifecycle WHERE chapter_id='chapter-1'"
            ))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun scalarInt(sql: String): Int = connection.createStatement().use { s ->
        s.executeQuery(sql).use { r -> check(r.next()); r.getInt(1) }
    }

    private fun scalarString(sql: String): String = connection.createStatement().use { s ->
        s.executeQuery(sql).use { r -> check(r.next()); r.getString(1) }
    }
}
