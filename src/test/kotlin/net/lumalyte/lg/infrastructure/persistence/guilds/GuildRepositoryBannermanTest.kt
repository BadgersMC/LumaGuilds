package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

/**
 * Tests for GuildRepository bannerman_enabled persistence functionality.
 * These tests verify that bannerman_enabled is properly saved to and loaded from the database.
 */
class GuildRepositoryBannermanTest {

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

        // Create guilds table with all columns including bannerman
        createGuildsTable()
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `new guild should have bannermanEnabled false after round-trip`() {
        // Given: A new guild with default bannermanEnabled
        val guildId = UUID.randomUUID()
        val guild = Guild(
            id = guildId,
            name = "New Guild",
            createdAt = Instant.now()
        )

        // When: Save guild to database and reload
        insertGuild(guild)
        val loaded = loadGuild(guildId)

        // Then: bannermanEnabled should be false
        assertNotNull(loaded)
        assertFalse(loaded!!.bannermanEnabled, "bannermanEnabled should default to false")
    }

    @Test
    fun `updating guild with bannermanEnabled true should persist correctly`() {
        // Given: An existing guild with bannermanEnabled false
        val guildId = UUID.randomUUID()
        insertGuildDirectly(guildId, "Test Guild", bannermanEnabled = 0)

        // When: Update guild to enable bannerman
        updateGuildBannerman(guildId, bannermanEnabled = true)

        // Then: Load and verify bannermanEnabled is true
        val loaded = loadGuild(guildId)
        assertNotNull(loaded)
        assertTrue(loaded!!.bannermanEnabled, "bannermanEnabled should be true after update")
    }

    @Test
    fun `saving Guild with bannermanEnabled true should persist correctly`() {
        // Given: A guild with bannerman enabled
        val guildId = UUID.randomUUID()
        val guild = Guild(
            id = guildId,
            name = "Bannerman Guild",
            createdAt = Instant.now(),
            bannermanEnabled = true
        )

        // When: Save guild to database
        insertGuild(guild)

        // Then: bannerman_enabled field should be persisted correctly
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT bannerman_enabled FROM guilds WHERE id = '$guildId'").use { rs ->
                assertTrue(rs.next(), "Guild should exist in database")
                assertEquals(1, rs.getInt("bannerman_enabled"), "bannerman_enabled should be 1 (true)")
            }
        }
    }

    @Test
    fun `loading Guild with bannermanEnabled false should return correct value`() {
        // Given: A guild with bannerman disabled in database
        val guildId = UUID.randomUUID()
        insertGuildDirectly(guildId, "Disabled Guild", bannermanEnabled = 0)

        // When: Load guild from database
        val guild = loadGuild(guildId)

        // Then: bannermanEnabled should be false
        assertNotNull(guild)
        assertFalse(guild!!.bannermanEnabled, "bannermanEnabled should be false")
    }

    @Test
    fun `existing guilds without bannerman column should default to false`() {
        // Given: A guild row without explicit bannerman value (simulating migration)
        val guildId = UUID.randomUUID()

        // Insert guild using SQL that mimics pre-migration data (defaults apply)
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at)
                VALUES ('$guildId', 'Legacy Guild', 1, 0, 'hostile', datetime('now'))
            """.trimIndent())
        }

        // When: Load guild from database
        val guild = loadGuild(guildId)

        // Then: bannermanEnabled should have default value (false)
        assertNotNull(guild)
        assertFalse(guild!!.bannermanEnabled, "bannermanEnabled should default to false")
    }

    @Test
    fun `toggling bannermanEnabled back to false should persist correctly`() {
        // Given: A guild with bannerman enabled
        val guildId = UUID.randomUUID()
        insertGuildDirectly(guildId, "Toggle Guild", bannermanEnabled = 1)

        // When: Update to disable bannerman
        updateGuildBannerman(guildId, bannermanEnabled = false)

        // Then: Load and verify bannermanEnabled is false
        val loaded = loadGuild(guildId)
        assertNotNull(loaded)
        assertFalse(loaded!!.bannermanEnabled, "bannermanEnabled should be false after toggle")
    }

    // Helper methods

    private fun createGuildsTable() {
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
                    mode TEXT NOT NULL DEFAULT 'hostile',
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
                    tracking_enabled INTEGER DEFAULT 1,
                    bank_frozen INTEGER DEFAULT 0,
                    bannerman_enabled INTEGER DEFAULT 0,
                    ally_home_world TEXT,
                    ally_home_x INTEGER,
                    ally_home_y INTEGER,
                    ally_home_z INTEGER,
                    ally_home_allowed_guilds TEXT
                )
            """.trimIndent())
        }
    }

    private fun insertGuild(guild: Guild) {
        val sql = """
            INSERT INTO guilds (id, name, level, bank_balance, mode, created_at, bannerman_enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, guild.id.toString())
            stmt.setString(2, guild.name)
            stmt.setInt(3, guild.level)
            stmt.setInt(4, guild.bankBalance)
            stmt.setString(5, guild.mode.name.lowercase())
            stmt.setString(6, guild.createdAt.toString())
            stmt.setInt(7, if (guild.bannermanEnabled) 1 else 0)
            stmt.executeUpdate()
        }
    }

    private fun insertGuildDirectly(guildId: UUID, name: String, bannermanEnabled: Int) {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at, bannerman_enabled)
                VALUES ('$guildId', '$name', 1, 0, 'hostile', datetime('now'), $bannermanEnabled)
            """.trimIndent())
        }
    }

    private fun updateGuildBannerman(guildId: UUID, bannermanEnabled: Boolean) {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                UPDATE guilds SET bannerman_enabled = ${if (bannermanEnabled) 1 else 0}
                WHERE id = '$guildId'
            """.trimIndent())
        }
    }

    private fun loadGuild(guildId: UUID): Guild? {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM guilds WHERE id = '$guildId'").use { rs ->
                if (!rs.next()) return null

                val name = rs.getString("name")
                val level = rs.getInt("level")
                val bankBalance = rs.getInt("bank_balance")
                val mode = GuildMode.valueOf(rs.getString("mode").uppercase())
                val createdAtStr = rs.getString("created_at")
                val createdAt = try {
                    Instant.parse(createdAtStr)
                } catch (e: Exception) {
                    Instant.now()
                }
                val bannermanEnabled = rs.getInt("bannerman_enabled") == 1

                return Guild(
                    id = guildId,
                    name = name,
                    level = level,
                    bankBalance = bankBalance,
                    mode = mode,
                    createdAt = createdAt,
                    bannermanEnabled = bannermanEnabled
                )
            }
        }
    }
}
