package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Tests for database migration v19 which introduces the `guild_homes` table for
 * persisting multiple named homes per guild.
 *
 * Before v19, `Guild.homes: Map<String, GuildHome>` was flattened to the single
 * `guilds.home_*` column set on every save, dropping every home that wasn't the default.
 * The non-default homes survived in memory until the next restart and then vanished.
 *
 * These tests pin down: the table exists after migration, existing single homes are
 * backfilled as `name = 'main'`, multiple homes per guild round-trip through SQL, and
 * the migration is idempotent.
 */
class GuildHomesMigrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var sqliteFile: File
    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        sqliteFile = tempDir.resolve("test.db").toFile()
        connection = DriverManager.getConnection("jdbc:sqlite:${sqliteFile.absolutePath}")
        connection.autoCommit = true
        // Foreign keys are off by default in SQLite; turn them on so the FK behaves like prod.
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `migration v19 creates guild_homes table`() {
        createGuildsTableV18()

        migrateToV19()

        assertTrue(tableExists("guild_homes"), "guild_homes table should exist after migration")
    }

    @Test
    fun `migration v19 backfills existing main home from legacy columns`() {
        // Given a v18 guild with the legacy home columns populated
        createGuildsTableV18()
        val guildId = UUID.randomUUID()
        val worldId = UUID.randomUUID()
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at,
                                    home_world, home_x, home_y, home_z)
                VALUES ('$guildId', 'OldGuild', 1, 0, 'Hostile', datetime('now'),
                        '$worldId', 100, 64, -200)
                """.trimIndent()
            )
        }

        // When we run the v19 migration
        migrateToV19()

        // Then the legacy home is now in guild_homes as 'main'
        connection.prepareStatement(
            "SELECT name, world_id, x, y, z FROM guild_homes WHERE guild_id = ?"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next(), "Backfilled home row should exist")
                assertEquals("main", rs.getString("name"))
                assertEquals(worldId.toString(), rs.getString("world_id"))
                assertEquals(100, rs.getInt("x"))
                assertEquals(64, rs.getInt("y"))
                assertEquals(-200, rs.getInt("z"))
                assertFalse(rs.next(), "Should be exactly one backfilled row per guild")
            }
        }
    }

    @Test
    fun `migration v19 does not backfill when legacy columns are null`() {
        // Given a v18 guild with no home set
        createGuildsTableV18()
        val guildId = UUID.randomUUID()
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at)
                VALUES ('$guildId', 'NoHomeGuild', 1, 0, 'Hostile', datetime('now'))
                """.trimIndent()
            )
        }

        migrateToV19()

        connection.prepareStatement(
            "SELECT COUNT(*) FROM guild_homes WHERE guild_id = ?"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1), "No row should be backfilled when legacy home is null")
            }
        }
    }

    @Test
    fun `guild_homes round-trips multiple named homes per guild`() {
        // Given a migrated DB with one guild
        createGuildsTableV18()
        migrateToV19()
        val guildId = UUID.randomUUID()
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at)
                VALUES ('$guildId', 'MultiHomeGuild', 1, 0, 'Hostile', datetime('now'))
                """.trimIndent()
            )
        }

        // When we insert several named homes
        val world = UUID.randomUUID().toString()
        connection.prepareStatement(
            "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { stmt ->
            listOf(
                Triple("main", 0, 64),
                Triple("farm", 100, 70),
                Triple("nether", -2000, 30)
            ).forEach { (name, x, y) ->
                stmt.setString(1, guildId.toString())
                stmt.setString(2, name)
                stmt.setString(3, world)
                stmt.setInt(4, x)
                stmt.setInt(5, y)
                stmt.setInt(6, 0)
                stmt.executeUpdate()
            }
        }

        // Then all three round-trip
        val readBack = mutableMapOf<String, Triple<Int, Int, Int>>()
        connection.prepareStatement(
            "SELECT name, x, y, z FROM guild_homes WHERE guild_id = ? ORDER BY name"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    readBack[rs.getString("name")] =
                        Triple(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                }
            }
        }
        assertEquals(3, readBack.size)
        assertEquals(Triple(0, 64, 0), readBack["main"])
        assertEquals(Triple(100, 70, 0), readBack["farm"])
        assertEquals(Triple(-2000, 30, 0), readBack["nether"])
    }

    @Test
    fun `guild_homes primary key prevents duplicate names within a guild`() {
        createGuildsTableV18()
        migrateToV19()
        val guildId = UUID.randomUUID()
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at)
                VALUES ('$guildId', 'DupGuild', 1, 0, 'Hostile', datetime('now'))
                """.trimIndent()
            )
        }
        val world = UUID.randomUUID().toString()

        // First insert succeeds
        connection.prepareStatement(
            "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.setString(2, "main")
            stmt.setString(3, world)
            stmt.setInt(4, 0)
            stmt.setInt(5, 64)
            stmt.setInt(6, 0)
            stmt.executeUpdate()
        }

        // Duplicate (guild_id, name) is rejected
        assertThrows(java.sql.SQLException::class.java) {
            connection.prepareStatement(
                "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, guildId.toString())
                stmt.setString(2, "main")
                stmt.setString(3, world)
                stmt.setInt(4, 1)
                stmt.setInt(5, 65)
                stmt.setInt(6, 1)
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `migration v19 is idempotent`() {
        createGuildsTableV18()
        val guildId = UUID.randomUUID()
        val worldId = UUID.randomUUID()
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at,
                                    home_world, home_x, home_y, home_z)
                VALUES ('$guildId', 'IdemGuild', 1, 0, 'Hostile', datetime('now'),
                        '$worldId', 1, 2, 3)
                """.trimIndent()
            )
        }

        migrateToV19()
        migrateToV19() // second run must not throw or duplicate the backfill row

        connection.prepareStatement(
            "SELECT COUNT(*) FROM guild_homes WHERE guild_id = ?"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1), "Backfill must be idempotent (INSERT OR IGNORE)")
            }
        }
    }

    @Test
    fun `removing a guild cascade-deletes its homes`() {
        createGuildsTableV18()
        migrateToV19()
        val guildId = UUID.randomUUID()
        val world = UUID.randomUUID().toString()

        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at)
                VALUES ('$guildId', 'CascadeGuild', 1, 0, 'Hostile', datetime('now'))
                """.trimIndent()
            )
        }
        connection.prepareStatement(
            "INSERT INTO guild_homes (guild_id, name, world_id, x, y, z) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.setString(2, "main")
            stmt.setString(3, world)
            stmt.setInt(4, 0); stmt.setInt(5, 64); stmt.setInt(6, 0)
            stmt.executeUpdate()
        }

        connection.createStatement().use { stmt ->
            stmt.execute("DELETE FROM guilds WHERE id = '$guildId'")
        }

        connection.prepareStatement(
            "SELECT COUNT(*) FROM guild_homes WHERE guild_id = ?"
        ).use { stmt ->
            stmt.setString(1, guildId.toString())
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1), "FK cascade must delete homes when the guild row is removed")
            }
        }
    }

    // -------- helpers (mirror SQLiteMigrations.migrateToVersion19) --------

    private fun createGuildsTableV18() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE guilds (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    banner TEXT,
                    emoji TEXT,
                    tag TEXT,
                    home_world TEXT,
                    home_x INTEGER,
                    home_y INTEGER,
                    home_z INTEGER,
                    level INTEGER NOT NULL DEFAULT 1,
                    bank_balance INTEGER NOT NULL DEFAULT 0,
                    mode TEXT NOT NULL DEFAULT 'Hostile',
                    mode_changed_at TEXT,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun migrateToV19() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_homes (
                    guild_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world_id TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (guild_id, name),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_guild_homes_guild_id ON guild_homes(guild_id)"
            )
            stmt.executeUpdate(
                """
                INSERT OR IGNORE INTO guild_homes (guild_id, name, world_id, x, y, z)
                SELECT id, 'main', home_world, home_x, home_y, home_z
                FROM guilds
                WHERE home_world IS NOT NULL
                  AND home_x IS NOT NULL
                  AND home_y IS NOT NULL
                  AND home_z IS NOT NULL
                """.trimIndent()
            )
        }
    }

    private fun tableExists(name: String): Boolean {
        connection.createStatement().use { stmt ->
            stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$name'"
            ).use { rs -> return rs.next() }
        }
    }
}
