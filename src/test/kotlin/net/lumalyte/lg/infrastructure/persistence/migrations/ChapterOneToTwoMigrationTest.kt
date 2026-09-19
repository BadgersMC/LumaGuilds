package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.domain.values.ProgressionCurve
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class ChapterOneToTwoMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection
    private val curve = ProgressionCurve(500.0, 1.15, 150, 100)

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("chapter-migration.db")}")
        createSchema()
        seedChapterOne()
    }

    @AfterEach
    fun tearDown() = connection.close()
    @Test
    fun `preview is read only and reports live guild migration plus orphan anomalies`() {
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)

        val preview = migration.preview("chapter-2-cutover", "chapter-1")

        assertEquals(2, preview.guilds.size)
        assertEquals(1, preview.orphanProgressionRows)
        assertEquals(1, preview.orphanHomeRows)
        assertEquals(2, preview.guilds.first { it.guildId == "g1" }.initialHomeCapacity)
        assertEquals(1, preview.guilds.first { it.guildId == "g2" }.initialHomeCapacity)
        assertEquals(10, int("SELECT current_level FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_migration_receipts"))
        assertEquals("BACKED_UP", text("SELECT phase FROM chapter_lifecycle WHERE chapter_id='chapter-1'"))
    }

    @Test
    fun `apply archives chapter one and initializes chapter two atomically`() {
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)

        val result = migration.apply("chapter-2-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)

        assertEquals(2, result.migratedGuilds)
        assertEquals(2, int("SELECT COUNT(*) FROM chapter_standings_archive WHERE chapter_id='chapter-1'"))
        assertEquals(1, int("SELECT current_level FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(0, int("SELECT total_experience FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(1, int("SELECT level FROM guilds WHERE id='g1'"))
        assertEquals(curve.experienceForNextLevel(1), int("SELECT experience_for_next_level FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(2, int("SELECT initial_home_capacity FROM guild_reward_accounts WHERE guild_id='g1'"))
        assertEquals(1, int("SELECT initial_home_capacity FROM guild_reward_accounts WHERE guild_id='g2'"))
        assertEquals(1000, int("SELECT elo FROM chapter_seasonal_ratings WHERE chapter_id='chapter-2' AND guild_id='g1'"))
        assertEquals(2, int("SELECT COUNT(*) FROM chapter_migration_receipts WHERE migration_id='chapter-2-cutover' AND status='APPLIED'"))
        assertEquals("RESET", text("SELECT phase FROM chapter_lifecycle WHERE chapter_id='chapter-1'"))
        assertEquals(777, int("SELECT balance FROM vault_gold WHERE guild_id='g1'"))
        assertEquals(1, int("SELECT COUNT(*) FROM members WHERE guild_id='g1'"))
        assertEquals(1, int("SELECT COUNT(*) FROM relations"))
        assertEquals(1, int("SELECT COUNT(*) FROM guild_progression WHERE guild_id='orphan-progression'"))

        val replay = migration.apply("chapter-2-cutover", "chapter-1", "chapter-2", 1_800_000_000_001)
        assertEquals(0, replay.migratedGuilds)
        assertTrue(replay.replayed)
    }

    @Test
    fun `zero guild migration records completion and replays successfully`() {
        connection.createStatement().use {
            it.execute("DELETE FROM guilds")
        }
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)

        val first = migration.apply("empty-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)
        assertEquals(0, first.migratedGuilds)
        assertTrue(!first.replayed)
        assertEquals(1, int("SELECT COUNT(*) FROM chapter_migrations WHERE migration_id='empty-cutover' AND source_chapter_id='chapter-1' AND target_chapter_id='chapter-2' AND status='COMPLETED'"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_migration_receipts WHERE migration_id='empty-cutover'"))

        val replay = migration.apply("empty-cutover", "chapter-1", "chapter-2", 1_800_000_000_001)
        assertEquals(0, replay.migratedGuilds)
        assertTrue(replay.replayed)
    }

    @Test
    fun `completed replay ignores later guild population changes`() {
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)
        migration.apply("population-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)

        connection.createStatement().use {
            it.execute("DELETE FROM guilds WHERE id='g2'")
            it.execute("INSERT INTO guilds VALUES ('g3','Gamma',1)")
            it.execute("INSERT INTO guild_progression VALUES ('g3',0,1,0,500,NULL,0,'[]',1,2)")
        }

        val replay = migration.apply("population-cutover", "chapter-1", "chapter-2", 1_800_000_000_001)
        assertEquals(0, replay.migratedGuilds)
        assertTrue(replay.replayed)
    }

    @Test
    fun `same migration id with different target is not treated as replay`() {
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)
        migration.apply("identity-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)

        assertThrows(IllegalStateException::class.java) {
            migration.apply("identity-cutover", "chapter-1", "chapter-3", 1_800_000_000_001)
        }
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_migrations WHERE migration_id='identity-cutover' AND target_chapter_id='chapter-3'"))
    }

    @Test
    fun `migration preserves bigint historical experience in archive and receipt`() {
        val historicalExperience = 3_000_000_000L
        connection.createStatement().use {
            it.execute("UPDATE guild_progression SET total_experience=$historicalExperience WHERE guild_id='g1'")
        }
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)

        val preview = migration.preview("big-xp-cutover", "chapter-1", "chapter-2")
        assertEquals(historicalExperience, preview.guilds.first { it.guildId == "g1" }.beforeExperience)

        migration.apply("big-xp-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)
        assertEquals(historicalExperience, long("SELECT run_experience FROM chapter_standings_archive WHERE chapter_id='chapter-1' AND guild_id='g1'"))
        assertEquals(historicalExperience, long("SELECT before_experience FROM chapter_migration_receipts WHERE migration_id='big-xp-cutover' AND guild_id='g1'"))
    }

    @Test
    fun `apply refuses to reset anything without a verified restorable backup`() {
        connection.createStatement().use {
            it.execute("UPDATE chapter_backup_evidence SET restore_verified_at = NULL WHERE backup_id='backup-1'")
        }
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)

        assertThrows(IllegalStateException::class.java) {
            migration.apply("chapter-2-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)
        }

        assertEquals(10, int("SELECT current_level FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_standings_archive"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_migration_receipts"))
        assertEquals("BACKED_UP", text("SELECT phase FROM chapter_lifecycle WHERE chapter_id='chapter-1'"))
    }

    @Test
    fun `conflicting preexisting chapter two account rolls back every guild change`() {
        connection.createStatement().use {
            it.execute("INSERT INTO guild_reward_accounts (guild_id, version, initial_home_capacity, prestige_count) VALUES ('g2',0,1,0)")
        }
        val migration = ChapterOneToTwoMigrationSQL(connection, mariaDb = false, curve)

        assertThrows(IllegalStateException::class.java) {
            migration.apply("chapter-2-cutover", "chapter-1", "chapter-2", 1_800_000_000_000)
        }

        assertEquals(10, int("SELECT current_level FROM guild_progression WHERE guild_id='g1'"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_standings_archive"))
        assertEquals(0, int("SELECT COUNT(*) FROM chapter_migration_receipts"))
        assertEquals("BACKED_UP", text("SELECT phase FROM chapter_lifecycle WHERE chapter_id='chapter-1'"))
    }
    private fun createSchema() {
        connection.createStatement().use { s ->
            s.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT NOT NULL, level INTEGER NOT NULL)")
            s.execute("""CREATE TABLE guild_progression (
                guild_id TEXT PRIMARY KEY, total_experience INTEGER NOT NULL, current_level INTEGER NOT NULL,
                experience_this_level INTEGER NOT NULL, experience_for_next_level INTEGER NOT NULL,
                last_level_up INTEGER, total_level_ups INTEGER NOT NULL, unlocked_perks TEXT NOT NULL,
                created_at INTEGER NOT NULL, last_updated INTEGER NOT NULL
            )""")
            s.execute("CREATE TABLE guild_homes (guild_id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(guild_id,name))")
            s.execute("CREATE TABLE vault_gold (guild_id TEXT PRIMARY KEY, balance INTEGER NOT NULL)")
            s.execute("CREATE TABLE members (player_id TEXT PRIMARY KEY, guild_id TEXT NOT NULL)")
            s.execute("CREATE TABLE relations (id TEXT PRIMARY KEY)")
        }
        ChapterLifecycleSchema.create(connection, mariaDb = false)
    }

    private fun seedChapterOne() {
        connection.createStatement().use { s ->
            s.execute("INSERT INTO guilds VALUES ('g1','Alpha',10)")
            s.execute("INSERT INTO guilds VALUES ('g2','Beta',5)")
            s.execute("INSERT INTO guild_progression VALUES ('g1',10000,10,100,999,NULL,9,'[\"OLD\"]',1,2)")
            s.execute("INSERT INTO guild_progression VALUES ('g2',2000,5,50,999,NULL,4,'[]',1,2)")
            s.execute("INSERT INTO guild_progression VALUES ('orphan-progression',999999,99,0,1,NULL,98,'[]',1,2)")
            s.execute("INSERT INTO guild_homes VALUES ('g1','main')")
            s.execute("INSERT INTO guild_homes VALUES ('g1','farm')")
            s.execute("INSERT INTO guild_homes VALUES ('orphan-home','ghost')")
            s.execute("INSERT INTO vault_gold VALUES ('g1',777)")
            s.execute("INSERT INTO members VALUES ('p1','g1')")
            s.execute("INSERT INTO relations VALUES ('r1')")
            s.execute("""INSERT INTO chapter_lifecycle
                (chapter_id,chapter_name,phase,updated_at,version,backup_id)
                VALUES ('chapter-1','Chapter 1','BACKED_UP',1,0,'backup-1')""")
            s.execute("""INSERT INTO chapter_backup_evidence
                (backup_id,chapter_id,storage_ref,sha256,size_bytes,created_at,verified_at,verification_status,restore_verified_at)
                VALUES ('backup-1','chapter-1','backup.db','abc',123,1,2,'VERIFIED',3)""")
        }
    }

    private fun int(sql: String): Int = connection.createStatement().use { s ->
        s.executeQuery(sql).use { r -> check(r.next()); r.getInt(1) }
    }

    private fun long(sql: String): Long = connection.createStatement().use { s ->
        s.executeQuery(sql).use { r -> check(r.next()); r.getLong(1) }
    }

    private fun text(sql: String): String = connection.createStatement().use { s ->
        s.executeQuery(sql).use { r -> check(r.next()); r.getString(1) }
    }
}
