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

/**
 * Tests for database migration v14 which adds join fee columns to guilds table.
 */
class JoinFeeMigrationTest {

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
    fun `migration v14 should add join_fee_enabled column to guilds table`() {
        // Given: Create guilds table without join fee columns (v13 state)
        createGuildsTableV13()

        // When: Run migration to v14
        migrateToV14()

        // Then: join_fee_enabled column should exist
        assertTrue(columnExists("guilds", "join_fee_enabled"),
            "join_fee_enabled column should exist after migration")
    }

    @Test
    fun `migration v14 should add join_fee_amount column to guilds table`() {
        // Given: Create guilds table without join fee columns (v13 state)
        createGuildsTableV13()

        // When: Run migration to v14
        migrateToV14()

        // Then: join_fee_amount column should exist
        assertTrue(columnExists("guilds", "join_fee_amount"),
            "join_fee_amount column should exist after migration")
    }

    @Test
    fun `join_fee_enabled should default to 0 (false) for SQLite`() {
        // Given: Create guilds table and run migration
        createGuildsTableV13()
        migrateToV14()

        // When: Insert a new guild without specifying join fee columns
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode)
                VALUES ('test-guild-id', 'Test Guild', datetime('now'), 1, 0, 'Hostile')
            """.trimIndent())
        }

        // Then: join_fee_enabled should default to 0
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_enabled FROM guilds WHERE id = 'test-guild-id'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("join_fee_enabled"),
                    "join_fee_enabled should default to 0 (false)")
            }
        }
    }

    @Test
    fun `join_fee_amount should default to 0 for SQLite`() {
        // Given: Create guilds table and run migration
        createGuildsTableV13()
        migrateToV14()

        // When: Insert a new guild without specifying join fee columns
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode)
                VALUES ('test-guild-id', 'Test Guild', datetime('now'), 1, 0, 'Hostile')
            """.trimIndent())
        }

        // Then: join_fee_amount should default to 0
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_amount FROM guilds WHERE id = 'test-guild-id'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("join_fee_amount"),
                    "join_fee_amount should default to 0")
            }
        }
    }

    @Test
    fun `migration should preserve existing guild data`() {
        // Given: Create guilds table with existing data
        createGuildsTableV13()

        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode, is_open)
                VALUES ('existing-guild', 'Existing Guild', datetime('now'), 5, 1000, 'Peaceful', 1)
            """.trimIndent())
        }

        // When: Run migration to v14
        migrateToV14()

        // Then: Existing data should be preserved
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM guilds WHERE id = 'existing-guild'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Existing Guild", rs.getString("name"))
                assertEquals(5, rs.getInt("level"))
                assertEquals(1000, rs.getInt("bank_balance"))
                assertEquals("Peaceful", rs.getString("mode"))
                assertEquals(1, rs.getInt("is_open"))
                // New columns should have defaults
                assertEquals(0, rs.getInt("join_fee_enabled"))
                assertEquals(0, rs.getInt("join_fee_amount"))
            }
        }
    }

    @Test
    fun `migration should be idempotent - running twice should not fail`() {
        // Given: Create guilds table
        createGuildsTableV13()

        // When: Run migration twice
        migrateToV14()
        migrateToV14() // Second run should not fail

        // Then: Columns should still exist and work correctly
        assertTrue(columnExists("guilds", "join_fee_enabled"))
        assertTrue(columnExists("guilds", "join_fee_amount"))
    }

    @Test
    fun `join_fee_enabled should accept integer values 0 and 1`() {
        // Given: Migrated database
        createGuildsTableV13()
        migrateToV14()

        // When: Insert guilds with join_fee_enabled = 0 and 1
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode, join_fee_enabled)
                VALUES ('guild-disabled', 'Disabled Fee Guild', datetime('now'), 1, 0, 'Hostile', 0)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode, join_fee_enabled)
                VALUES ('guild-enabled', 'Enabled Fee Guild', datetime('now'), 1, 0, 'Hostile', 1)
            """.trimIndent())
        }

        // Then: Both values should be stored correctly
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_enabled FROM guilds WHERE id = 'guild-disabled'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("join_fee_enabled"))
            }

            stmt.executeQuery("SELECT join_fee_enabled FROM guilds WHERE id = 'guild-enabled'").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("join_fee_enabled"))
            }
        }
    }

    @Test
    fun `join_fee_amount should accept positive integer values`() {
        // Given: Migrated database
        createGuildsTableV13()
        migrateToV14()

        // When: Insert guild with join fee amount
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, created_at, level, bank_balance, mode, join_fee_enabled, join_fee_amount)
                VALUES ('guild-with-fee', 'Fee Guild', datetime('now'), 1, 0, 'Hostile', 1, 500)
            """.trimIndent())
        }

        // Then: Amount should be stored correctly
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_amount FROM guilds WHERE id = 'guild-with-fee'").use { rs ->
                assertTrue(rs.next())
                assertEquals(500, rs.getInt("join_fee_amount"))
            }
        }
    }

    // Helper methods

    private fun createGuildsTableV13() {
        // Create guilds table as it exists in v13 (with is_open but without join fee columns)
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
                    is_open INTEGER DEFAULT 0
                )
            """.trimIndent())
        }
    }

    private fun migrateToV14() {
        // Replicate the migration logic from SQLiteMigrations.migrateToVersion14()
        val sqlCommands = mutableListOf<String>()

        // Add join_fee_enabled column (if not exists)
        if (!columnExists("guilds", "join_fee_enabled")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN join_fee_enabled INTEGER DEFAULT 0;")
        }

        // Add join_fee_amount column (if not exists)
        if (!columnExists("guilds", "join_fee_amount")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN join_fee_amount INTEGER DEFAULT 0;")
        }

        sqlCommands.forEach { sql ->
            connection.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
    }

    private fun columnExists(tableName: String, columnName: String): Boolean {
        return try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name") == columnName) {
                            return true
                        }
                    }
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
