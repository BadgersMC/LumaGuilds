package net.lumalyte.lg.infrastructure.persistence.migrations

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bukkit.Server
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class ChapterLifecycleMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection
    private lateinit var plugin: JavaPlugin

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("chapter-lifecycle.db")}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA user_version = 29")
            statement.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, ally_home_allowed_guilds TEXT)")
            statement.execute("CREATE TABLE guild_homes (id TEXT PRIMARY KEY, allowed_ranks TEXT)")
            listOf(
                "members", "relations", "parties", "party_requests", "player_party_preferences",
                "bank_tx", "kills", "audits", "wars", "leaderboards", "guild_invitations",
                "vault_slots", "vault_gold", "vault_transaction_log", "guild_strikes",
                "guild_penalties", "quest_player_placed_blocks", "guild_experience_source_usage",
                "guild_bank_xp_high_water", "membership_history", "guild_gold_operations",
                "guild_gold_withdrawal_usage", "guild_gold_security"
            ).forEach { table ->
                statement.execute("CREATE TABLE $table (id TEXT PRIMARY KEY)")
            }
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
    fun `version 30 creates durable chapter lifecycle tables`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()

        assertTrue(tableExists("chapter_lifecycle"))
        assertTrue(tableExists("chapter_standings_archive"))
        assertTrue(tableExists("chapter_backup_evidence"))
        assertTrue(tableExists("chapter_migrations"))
        assertTrue(tableExists("chapter_migration_receipts"))
        assertTrue(tableExists("chapter_rated_pair_guards"))
        assertTrue(tableExists("chapter_rated_war_results"))
        assertTrue(tableExists("war_banners"))
        assertTrue(tableExists("war_notifications"))
        assertEquals(33, databaseVersion())
    }

    @Test
    fun `version 32 repairs a missing war banner table without version rollback`() {
        val migrations = SQLiteMigrations(plugin, connection, claimsEnabled = false)
        migrations.migrate()
        connection.createStatement().use { it.execute("DROP TABLE war_banners") }
        assertFalse(tableExists("war_banners"))
        assertEquals(33, databaseVersion())

        migrations.migrate()

        assertTrue(tableExists("war_banners"))
        assertEquals(33, databaseVersion())
    }

    @Test
    fun `version 33 repairs a missing war notification table without version rollback`() {
        val migrations = SQLiteMigrations(plugin, connection, claimsEnabled = false)
        migrations.migrate()
        connection.createStatement().use { it.execute("DROP TABLE war_notifications") }
        assertFalse(tableExists("war_notifications"))
        assertEquals(33, databaseVersion())

        migrations.migrate()

        assertTrue(tableExists("war_notifications"))
        assertEquals(33, databaseVersion())
    }

    private fun tableExists(table: String): Boolean = connection.prepareStatement(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
    ).use { statement ->
        statement.setString(1, table)
        statement.executeQuery().use { it.next() }
    }

    private fun databaseVersion(): Int = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA user_version").use { results ->
            check(results.next())
            results.getInt(1)
        }
    }
}
