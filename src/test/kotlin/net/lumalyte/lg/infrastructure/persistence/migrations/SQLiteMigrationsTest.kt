package net.lumalyte.lg.infrastructure.persistence.migrations

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import io.mockk.mockk
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLiteMigrationsTest {
    
    private lateinit var mockPlugin: JavaPlugin
    
    @TempDir
    lateinit var tempDir: File
    
    private lateinit var dbFile: File
    private lateinit var connection: Connection
    
    @BeforeEach
    fun setUp() {
        mockPlugin = mockk(relaxed = true)
        dbFile = File(tempDir, "test_claims.db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }
    
    @AfterEach
    fun tearDown() {
        connection.close()
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }
    
    @Test
    fun `test migration to version 3 creates all required tables`() {
        // Given: A fresh database
        val migrator = SQLiteMigrations(mockPlugin, connection)
        
        // When: Migration is run
        migrator.migrate()
        
        // Then: All required tables should exist
        val tables = getTableNames()
        
        // Core tables
        assertTrue(tables.contains("claims"))
        assertTrue(tables.contains("claim_partitions"))
        assertTrue(tables.contains("claim_default_permissions"))
        assertTrue(tables.contains("claim_flags"))
        assertTrue(tables.contains("claim_player_permissions"))
        
        // New guild tables
        assertTrue(tables.contains("guilds"))
        assertTrue(tables.contains("ranks"))
        assertTrue(tables.contains("members"))
        assertTrue(tables.contains("relations"))
        assertTrue(tables.contains("parties"))
        assertTrue(tables.contains("bank_tx"))
        assertTrue(tables.contains("kills"))
        assertTrue(tables.contains("wars"))
        assertTrue(tables.contains("leaderboards"))
        assertTrue(tables.contains("audits"))
        
        // Verify schema version is 4 (includes tag/emoji columns)
        assertEquals(4, getCurrentDatabaseVersion())
    }
    
    @Test
    fun `test claims table has team_id column`() {
        // Given: A fresh database
        val migrator = SQLiteMigrations(mockPlugin, connection)
        
        // When: Migration is run
        migrator.migrate()
        
        // Then: Claims table should have team_id column
        val columns = getTableColumns("claims")
        assertTrue(columns.contains("team_id"))
    }
    
    @Test
    fun `test guilds table has correct structure`() {
        // Given: A fresh database
        val migrator = SQLiteMigrations(mockPlugin, connection)

        // When: Migration is run
        migrator.migrate()

        // Then: Guilds table should have all required columns
        val columns = getTableColumns("guilds")
        assertTrue(columns.contains("id"))
        assertTrue(columns.contains("name"))
        assertTrue(columns.contains("banner"))
        assertTrue(columns.contains("emoji"))
        assertTrue(columns.contains("tag"))
        assertTrue(columns.contains("home_world"))
        assertTrue(columns.contains("home_x"))
        assertTrue(columns.contains("home_y"))
        assertTrue(columns.contains("home_z"))
        assertTrue(columns.contains("level"))
        assertTrue(columns.contains("bank_balance"))
        assertTrue(columns.contains("mode"))
        assertTrue(columns.contains("mode_changed_at"))
        assertTrue(columns.contains("created_at"))

        // Verify schema version is 4 (includes tag/emoji columns)
        assertEquals(4, getCurrentDatabaseVersion())
    }
    
    private fun getTableNames(): Set<String> {
        val tables = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
            while (rs.next()) {
                tables.add(rs.getString("name"))
            }
        }
        return tables
    }
    
    private fun getTableColumns(tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("PRAGMA table_info($tableName)")
            while (rs.next()) {
                columns.add(rs.getString("name"))
            }
        }
        return columns
    }
    
    private fun getCurrentDatabaseVersion(): Int {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("PRAGMA user_version")
            return if (rs.next()) rs.getInt(1) else 0
        }
    }
}
