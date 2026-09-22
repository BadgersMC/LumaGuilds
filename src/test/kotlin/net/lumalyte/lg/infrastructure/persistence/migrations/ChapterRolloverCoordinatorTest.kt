package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class ChapterRolloverCoordinatorTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var connection: Connection

    @BeforeEach fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("rollover.db"))
        connection.createStatement().use { s ->
            s.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT NOT NULL, level INTEGER NOT NULL)")
            s.execute("CREATE TABLE guild_progression (guild_id TEXT PRIMARY KEY,total_experience INTEGER NOT NULL,current_level INTEGER NOT NULL)")
            s.execute("CREATE TABLE vault_gold (guild_id TEXT PRIMARY KEY,balance INTEGER NOT NULL)")
            s.execute("INSERT INTO guilds VALUES ('g1','Alpha',42)")
            s.execute("INSERT INTO guild_progression VALUES ('g1',42000,42)")
        }
        ChapterLifecycleSchema.create(connection, false)
        SeasonalEloSchema.create(connection, false)
        connection.createStatement().use { s ->
            s.execute("INSERT INTO chapter_lifecycle (chapter_id,chapter_name,phase,starts_at,ends_at,updated_at,version) VALUES ('c2','Chapter 2','SCHEDULED',100,1000,100,0)")
            s.execute("INSERT INTO chapter_seasonal_ratings VALUES ('c2','g1',1337,200)")
            s.execute("INSERT INTO chapter_rated_pair_guards VALUES ('c2','g1','g2',900,'w1')")
            s.execute("INSERT INTO chapter_rated_war_results VALUES ('w1','c2','g1','g2',1300,1200,1337,1163,1.0,0.0,900)")
        }
    }
    @AfterEach fun tearDown() = connection.close()

    @Test fun notDueStaysScheduled() {
        assertEquals("SCHEDULED", coordinator().advance(plan(), 999).phase)
    }

    @Test fun restartCatchupCompletesWithoutResettingRunProgression() {
        val c = coordinator()
        assertEquals("FROZEN", c.advance(plan(), 1000).phase)
        connection.createStatement().use { s ->
            s.execute("UPDATE chapter_lifecycle SET phase='BACKED_UP', backup_id='b1' WHERE chapter_id='c2'")
            s.execute("INSERT INTO chapter_backup_evidence (backup_id,chapter_id,storage_ref,sha256,size_bytes,created_at,verified_at,verification_status,restore_verified_at) VALUES ('b1','c2','b.db','abc',1,1,1,'VERIFIED',1)")
        }
        val status = c.catchUp(plan(), 1100)
        assertEquals("COMPLETE", status.phase)
        assertEquals(42, int("SELECT current_level FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(42000, int("SELECT total_experience FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(1337, int("SELECT seasonal_elo FROM chapter_standings_archive WHERE chapter_id='c2' AND guild_id='g1'"))
        assertEquals(1000, int("SELECT elo FROM chapter_seasonal_ratings WHERE chapter_id='c3' AND guild_id='g1'"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_seasonal_ratings WHERE chapter_id='c2'"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_rated_pair_guards WHERE chapter_id='c2'"))
        assertEquals(1, int("SELECT COUNT(*) FROM chapter_rated_war_results WHERE war_id='w1' AND chapter_id='c2'"))
        assertEquals("SCHEDULED", text("SELECT phase FROM chapter_lifecycle WHERE chapter_id='c3'"))
    }

    @Test fun archiveWorkRollsBackWhenPhaseAdvanceFails() {
        connection.createStatement().use { s ->
            s.execute("UPDATE chapter_lifecycle SET phase='BACKED_UP', backup_id='b1' WHERE chapter_id='c2'")
            s.execute("INSERT INTO chapter_backup_evidence (backup_id,chapter_id,storage_ref,sha256,size_bytes,created_at,verified_at,verification_status,restore_verified_at) VALUES ('b1','c2','b.db','abc',1,1,1,'VERIFIED',1)")
            s.execute("""
                CREATE TRIGGER fail_archive_phase
                BEFORE UPDATE OF phase ON chapter_lifecycle
                WHEN OLD.chapter_id='c2' AND NEW.phase='ARCHIVED'
                BEGIN
                    SELECT RAISE(ABORT, 'phase write failed');
                END
            """.trimIndent())
        }

        val status = coordinator().advance(plan(), 1100)

        assertEquals("BACKED_UP", status.phase)
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_standings_archive WHERE chapter_id='c2'"))
        assertTrue(status.lastError!!.contains("phase write failed"))
    }

    @Test fun backupFailureIsPersistedAndPhaseRemainsFrozen() {
        val c = ChapterRolloverCoordinatorSQL(connection) { _, _, _ -> error("disk full") }
        c.advance(plan(), 1000)
        val status = c.advance(plan(), 1001)
        assertEquals("FROZEN", status.phase)
        assertTrue(status.lastError!!.contains("disk full"))
    }

    private fun coordinator() = ChapterRolloverCoordinatorSQL(connection) { _, _, _ -> error("backup should be injected externally") }
    private fun plan() = ChapterRolloverPlan("c2","Chapter 2","c3","Chapter 3",1000,2000)
    private fun int(sql:String)=connection.createStatement().use{it.executeQuery(sql).use{r->check(r.next());r.getInt(1)}}
    private fun text(sql:String)=connection.createStatement().use{it.executeQuery(sql).use{r->check(r.next());r.getString(1)}}
}
