package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Tests for database migration v22 which adds bannerman_enabled column to guilds table.
 */
@Suppress("LateinitUsage")
internal class BannermanMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var sqliteFile: File
    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        // Create temporary SQLite database
        sqliteFile = tempDir.resolve("test.db").toFile()
        connection = DriverManager.getConnection("jdbc:sqlite:${sqliteFile.absolutePath}")
        connection.autoCommit = true
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun v22AddsBannermanColumn() {
        // Given: Create guilds table without bannerman_enabled (v21 state)
        createGuildsTableV21()

        // When: Run migration to v22
        migrateToV22()

        // Then: bannerman_enabled column should exist
        assertTrue(columnExists("guilds", "bannerman_enabled"),
            "bannerman_enabled column should exist after migration")
    }

    @Test
    fun bannermanDefaultsToFalse() {
        // Given: Create guilds table and run migration
        createGuildsTableV21()
        migrateToV22()

        // When: Insert a new guild without specifying bannerman_enabled
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode)
                VALUES ('g1', 'G', datetime('now'), 1, 0, 'Hostile')
            """.trimIndent())
        }

        // Then: bannerman_enabled should default to 0
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT bannerman_enabled FROM guilds WHERE id='g1'").use { rs ->
                assertTrue(rs.next())
                assertEquals(
                    0,
                    rs.getInt("bannerman_enabled"),
                    "bannerman_enabled should default to 0 (false)",
                )
            }
        }
    }

    @Test
    fun migrationPreservesRows() {
        // Given: Create guilds table with existing data
        createGuildsTableV21()

        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode)
                VALUES ('keep', 'KeepMe', datetime('now'), 3, 50, 'Peaceful')
            """.trimIndent())
        }

        // When: Run migration to v22
        migrateToV22()

        // Then: Existing data should be preserved
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name, level, bannerman_enabled FROM guilds WHERE id='keep'").use { rs ->
                assertTrue(rs.next())
                assertEquals("KeepMe", rs.getString("name"))
                assertEquals(3, rs.getInt("level"))
                assertEquals(0, rs.getInt("bannerman_enabled"))
            }
        }
    }

    @Test
    fun migrationIsIdempotent() {
        // Given: Create guilds table
        createGuildsTableV21()

        // When: Run migration twice
        migrateToV22()
        migrateToV22() // Second run should not fail

        // Then: Column should still exist
        assertTrue(columnExists("guilds", "bannerman_enabled"))
    }

    @Test
    fun bannermanAcceptsZeroAndOne() {
        // Given: Migrated database
        createGuildsTableV21()
        migrateToV22()

        // When: Insert guilds with bannerman_enabled = 0 and 1
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode, bannerman_enabled)
                VALUES ('guild-disabled', 'Disabled', datetime('now'), 1, 0, 'Hostile', 0)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode, bannerman_enabled)
                VALUES ('guild-enabled', 'Enabled', datetime('now'), 1, 0, 'Hostile', 1)
            """.trimIndent())
        }

        // Then: Both values should be stored correctly
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT bannerman_enabled FROM guilds WHERE id = 'guild-disabled'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("bannerman_enabled"))
            }

            stmt.executeQuery("SELECT bannerman_enabled FROM guilds WHERE id = 'guild-enabled'").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("bannerman_enabled"))
            }
        }
    }

    // Helper methods

    private fun createGuildsTableV21() {
        // Create guilds table as it exists in v21 (with all columns up to v21, but without bannerman_enabled)
        connection.createStatement().use { stmt ->
            stmt.execute("""
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
                    created_at TEXT NOT NULL,
                    vault_status TEXT DEFAULT 'NEVER_PLACED',
                    vault_chest_world TEXT,
                    vault_chest_x INTEGER,
                    vault_chest_y INTEGER,
                    vault_chest_z INTEGER,
                    is_open INTEGER DEFAULT 0,
                    join_fee_enabled INTEGER DEFAULT 0,
                    join_fee_amount INTEGER DEFAULT 0,
                    ally_home_allowed_guilds TEXT
                )
            """.trimIndent())
        }
    }

    private fun migrateToV22() {
        // Replicate the migration logic from SQLiteMigrations.migrateToVersion22()
        val sqlCommands = mutableListOf<String>()

        // Add bannerman_enabled column (if not exists)
        if (!columnExists("guilds", "bannerman_enabled")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN bannerman_enabled INTEGER DEFAULT 0;")
        }

        sqlCommands.forEach { sql ->
            connection.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
    }

    /**
     * Fails fast on metadata query errors so genuine DB problems don't masquerade as a missing
     * column. Test-helper only — production code in GuildRepositorySQLite has its own variant
     * that logs and returns false because runtime persistence should be resilient.
     */
    private fun columnExists(tableName: String, columnName: String): Boolean {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == columnName) {
                        return true
                    }
                }
                return false
            }
        }
    }
}
