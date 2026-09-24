package net.lumalyte.lg.infrastructure.persistence.migrations

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bukkit.Server
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class InvitationStatisticsMigrationTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var connection: Connection
    private lateinit var plugin: JavaPlugin

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("invite-stats.db")}")
        connection.createStatement().use {
            it.execute("PRAGMA user_version = 36")
            it.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, ally_home_allowed_guilds TEXT)")
            it.execute("CREATE TABLE guild_homes (id TEXT PRIMARY KEY, allowed_ranks TEXT)")
        }
        val pluginManager = mockk<PluginManager>(relaxed = true)
        val server = mockk<Server> { every { getPluginManager() } returns pluginManager }
        plugin = mockk(relaxed = true) {
            every { getComponentLogger() } returns mockk<ComponentLogger>(relaxed = true)
            every { getServer() } returns server
        }
    }

    @AfterEach fun tearDown() = connection.close()

    @Test
    fun `version 37 creates durable guild invitation history`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()

        assertTrue(tableExists("guild_invitation_history"))
        assertTrue(columnExists("guild_invitation_history", "inviter_player_id"))
        assertTrue(columnExists("guild_invitation_history", "invited_player_id"))
        assertTrue(columnExists("guild_invitation_history", "sent_at"))
        assertEquals(40, databaseVersion())
    }

    private fun tableExists(table: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").use {
            it.setString(1, table)
            it.executeQuery().use { rows -> rows.next() }
        }
    private fun columnExists(table: String, column: String): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                generateSequence { if (rows.next()) rows.getString("name") else null }.any { it == column }
            }
        }

    private fun databaseVersion(): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
}
