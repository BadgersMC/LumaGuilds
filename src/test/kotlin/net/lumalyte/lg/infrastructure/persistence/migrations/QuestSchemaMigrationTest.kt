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

class QuestSchemaMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection
    private lateinit var plugin: JavaPlugin

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("quest-schema.db")}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA user_version = 35")
            statement.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, ally_home_allowed_guilds TEXT)")
            statement.execute("CREATE TABLE guild_homes (id TEXT PRIMARY KEY, allowed_ranks TEXT)")
        }
        val pluginManager = mockk<PluginManager>(relaxed = true)
        val server = mockk<Server> {
            every { getPluginManager() } returns pluginManager
        }
        plugin = mockk(relaxed = true) {
            every { getComponentLogger() } returns mockk<ComponentLogger>(relaxed = true)
            every { getServer() } returns server
        }
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `version 36 owns weekly quest persistence schema`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()

        listOf(
            "weekly_quest_sets",
            "weekly_quest_definitions",
            "guild_quest_progress",
            "guild_quest_weekly_bonus",
            "quest_leaderboard_payouts"
        ).forEach { table -> assertTrue(tableExists(table), "Missing $table") }

        assertTrue(columnExists("weekly_quest_definitions", "target_rarity"))
        assertTrue(columnExists("weekly_quest_definitions", "conditions"))
        assertTrue(columnExists("weekly_quest_definitions", "quest_order"))
        assertEquals(39, databaseVersion())
    }

    private fun tableExists(table: String): Boolean = connection.prepareStatement(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
    ).use { statement ->
        statement.setString(1, table)
        statement.executeQuery().use { it.next() }
    }

    private fun columnExists(table: String, column: String): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { results ->
                generateSequence { if (results.next()) results.getString("name") else null }
                    .any { it == column }
            }
        }

    private fun databaseVersion(): Int = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA user_version").use { results ->
            check(results.next())
            results.getInt(1)
        }
    }
}
