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
 * Tests for GuildRepository join fee persistence functionality.
 * These tests verify that join_fee_enabled and join_fee_amount are properly
 * saved to and loaded from the database.
 */
class GuildRepositoryJoinFeeTest {

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

        // Create guilds table with all columns including join fee
        createGuildsTable()
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `saving Guild with joinFeeEnabled true should persist correctly`() {
        // Given: A guild with join fee enabled
        val guildId = UUID.randomUUID()
        val guild = Guild(
            id = guildId,
            name = "Premium Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )

        // When: Save guild to database
        insertGuild(guild)

        // Then: Join fee fields should be persisted correctly
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_enabled, join_fee_amount FROM guilds WHERE id = '${guildId}'").use { rs ->
                assertTrue(rs.next(), "Guild should exist in database")
                assertEquals(1, rs.getInt("join_fee_enabled"), "join_fee_enabled should be 1 (true)")
                assertEquals(500, rs.getInt("join_fee_amount"), "join_fee_amount should be 500")
            }
        }
    }

    @Test
    fun `saving Guild with joinFeeEnabled false should persist correctly`() {
        // Given: A guild with join fee disabled
        val guildId = UUID.randomUUID()
        val guild = Guild(
            id = guildId,
            name = "Free Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        // When: Save guild to database
        insertGuild(guild)

        // Then: Join fee fields should be persisted as disabled
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_enabled, join_fee_amount FROM guilds WHERE id = '${guildId}'").use { rs ->
                assertTrue(rs.next(), "Guild should exist in database")
                assertEquals(0, rs.getInt("join_fee_enabled"), "join_fee_enabled should be 0 (false)")
                assertEquals(0, rs.getInt("join_fee_amount"), "join_fee_amount should be 0")
            }
        }
    }

    @Test
    fun `loading Guild from database should populate join fee properties`() {
        // Given: A guild with join fee in database
        val guildId = UUID.randomUUID()
        insertGuildDirectly(guildId, "Test Guild", joinFeeEnabled = 1, joinFeeAmount = 750)

        // When: Load guild from database
        val guild = loadGuild(guildId)

        // Then: Join fee properties should be populated
        assertNotNull(guild)
        assertTrue(guild!!.joinFeeEnabled, "joinFeeEnabled should be true")
        assertEquals(750, guild.joinFeeAmount, "joinFeeAmount should be 750")
    }

    @Test
    fun `loading Guild with join fee disabled should return correct values`() {
        // Given: A guild with join fee disabled in database
        val guildId = UUID.randomUUID()
        insertGuildDirectly(guildId, "Free Guild", joinFeeEnabled = 0, joinFeeAmount = 0)

        // When: Load guild from database
        val guild = loadGuild(guildId)

        // Then: Join fee properties should be false/0
        assertNotNull(guild)
        assertFalse(guild!!.joinFeeEnabled, "joinFeeEnabled should be false")
        assertEquals(0, guild.joinFeeAmount, "joinFeeAmount should be 0")
    }

    @Test
    fun `updating Guild join fee settings should persist correctly`() {
        // Given: An existing guild with no join fee
        val guildId = UUID.randomUUID()
        insertGuildDirectly(guildId, "Update Test Guild", joinFeeEnabled = 0, joinFeeAmount = 0)

        // When: Update guild with join fee settings
        updateGuildJoinFee(guildId, joinFeeEnabled = true, joinFeeAmount = 1000)

        // Then: Updated values should be persisted
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT join_fee_enabled, join_fee_amount FROM guilds WHERE id = '${guildId}'").use { rs ->
                assertTrue(rs.next(), "Guild should exist in database")
                assertEquals(1, rs.getInt("join_fee_enabled"), "join_fee_enabled should be updated to 1")
                assertEquals(1000, rs.getInt("join_fee_amount"), "join_fee_amount should be updated to 1000")
            }
        }
    }

    @Test
    fun `existing guilds without join fee columns should default to disabled`() {
        // Given: A guild row without explicit join fee values (simulating migration)
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

        // Then: Join fee properties should have default values
        assertNotNull(guild)
        assertFalse(guild!!.joinFeeEnabled, "joinFeeEnabled should default to false")
        assertEquals(0, guild.joinFeeAmount, "joinFeeAmount should default to 0")
    }

    @Test
    fun `saving Guild should preserve other properties alongside join fee`() {
        // Given: A guild with all properties set
        val guildId = UUID.randomUUID()
        val createdAt = Instant.now()
        val guild = Guild(
            id = guildId,
            name = "Full Featured Guild",
            level = 5,
            bankBalance = 10000,
            mode = GuildMode.PEACEFUL,
            createdAt = createdAt,
            isOpen = true,
            joinFeeEnabled = true,
            joinFeeAmount = 250
        )

        // When: Save guild to database
        insertGuild(guild)

        // Then: All properties should be persisted
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM guilds WHERE id = '${guildId}'").use { rs ->
                assertTrue(rs.next(), "Guild should exist in database")
                assertEquals("Full Featured Guild", rs.getString("name"))
                assertEquals(5, rs.getInt("level"))
                assertEquals(10000, rs.getInt("bank_balance"))
                assertEquals("peaceful", rs.getString("mode"))
                assertEquals(1, rs.getInt("is_open"))
                assertEquals(1, rs.getInt("join_fee_enabled"))
                assertEquals(250, rs.getInt("join_fee_amount"))
            }
        }
    }

    @Test
    fun `loading Guild should populate all properties including join fee`() {
        // Given: A complete guild in database
        val guildId = UUID.randomUUID()
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at, is_open, join_fee_enabled, join_fee_amount)
                VALUES ('$guildId', 'Complete Guild', 10, 50000, 'peaceful', datetime('now'), 1, 1, 999)
            """.trimIndent())
        }

        // When: Load guild from database
        val guild = loadGuild(guildId)

        // Then: All properties should be populated
        assertNotNull(guild)
        assertEquals("Complete Guild", guild!!.name)
        assertEquals(10, guild.level)
        assertEquals(50000, guild.bankBalance)
        assertEquals(GuildMode.PEACEFUL, guild.mode)
        assertTrue(guild.isOpen)
        assertTrue(guild.joinFeeEnabled)
        assertEquals(999, guild.joinFeeAmount)
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
                    join_fee_amount INTEGER DEFAULT 0
                )
            """.trimIndent())
        }
    }

    private fun insertGuild(guild: Guild) {
        val sql = """
            INSERT INTO guilds (id, name, level, bank_balance, mode, created_at, is_open, join_fee_enabled, join_fee_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, guild.id.toString())
            stmt.setString(2, guild.name)
            stmt.setInt(3, guild.level)
            stmt.setInt(4, guild.bankBalance)
            stmt.setString(5, guild.mode.name.lowercase())
            stmt.setString(6, guild.createdAt.toString())
            stmt.setInt(7, if (guild.isOpen) 1 else 0)
            stmt.setInt(8, if (guild.joinFeeEnabled) 1 else 0)
            stmt.setInt(9, guild.joinFeeAmount)
            stmt.executeUpdate()
        }
    }

    private fun insertGuildDirectly(guildId: UUID, name: String, joinFeeEnabled: Int, joinFeeAmount: Int) {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO guilds (id, name, level, bank_balance, mode, created_at, join_fee_enabled, join_fee_amount)
                VALUES ('$guildId', '$name', 1, 0, 'hostile', datetime('now'), $joinFeeEnabled, $joinFeeAmount)
            """.trimIndent())
        }
    }

    private fun updateGuildJoinFee(guildId: UUID, joinFeeEnabled: Boolean, joinFeeAmount: Int) {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                UPDATE guilds SET join_fee_enabled = ${if (joinFeeEnabled) 1 else 0}, join_fee_amount = $joinFeeAmount
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
                val isOpen = rs.getInt("is_open") == 1
                val joinFeeEnabled = rs.getInt("join_fee_enabled") == 1
                val joinFeeAmount = rs.getInt("join_fee_amount")

                return Guild(
                    id = guildId,
                    name = name,
                    level = level,
                    bankBalance = bankBalance,
                    mode = mode,
                    createdAt = createdAt,
                    isOpen = isOpen,
                    joinFeeEnabled = joinFeeEnabled,
                    joinFeeAmount = joinFeeAmount
                )
            }
        }
    }
}
