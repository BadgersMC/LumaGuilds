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

class GuildGoldMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection
    private lateinit var plugin: JavaPlugin

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("migration.db")}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA user_version = 28")
            statement.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, ally_home_allowed_guilds TEXT)")
            statement.execute("CREATE TABLE guild_homes (id TEXT PRIMARY KEY, allowed_ranks TEXT)")
            listOf(
                "members", "relations", "parties", "party_requests", "player_party_preferences",
                "bank_tx", "kills", "audits", "wars", "leaderboards", "guild_invitations",
                "vault_slots", "vault_gold", "vault_transaction_log", "guild_strikes",
                "guild_penalties", "quest_player_placed_blocks", "guild_experience_source_usage",
                "guild_bank_xp_high_water", "membership_history"
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

    /** Releases the guild-gold migration fixture after each scenario. */
    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `version 29 creates canonical guild gold operation tables`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()

        assertTrue(tableExists("guild_gold_operations"))
        assertTrue(tableExists("guild_gold_withdrawal_usage"))
        assertTrue(tableExists("guild_gold_security"))
        assertTrue(tableExists(BankDepositXpSchema.DELIVERIES))
        assertTrue(tableExists(BankDepositXpSchema.SEQUENCES))
        assertEquals(BankContributionSchema.VERSION, databaseVersion())
    }

    @Test
    fun `version 47 retains legacy gold without inventing deposit quotes`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        connection.createStatement().use {
            it.execute("""INSERT INTO guild_gold_operations
                (transaction_id, guild_id, actor_id, route, direction, amount, fee, old_balance,
                 new_balance, status, description, created_at)
                VALUES ('legacy', 'guild', 'actor', 'PERSONAL_ACCOUNT', 'CREDIT', 100, 0, 0, 100,
                        'APPLIED', 'Historical deposit without XP provenance', 1)""".trimIndent())
            it.execute("PRAGMA user_version = 46")
        }

        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()

        assertEquals(BankContributionSchema.VERSION, databaseVersion())
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM guild_gold_operations WHERE transaction_id = 'legacy'").use {
                assertTrue(it.next())
                assertEquals(1, it.getInt(1))
            }
            statement.executeQuery("SELECT COUNT(*) FROM ${BankDepositXpSchema.DELIVERIES}").use {
                assertTrue(it.next())
                assertEquals(0, it.getInt(1))
            }
        }
    }

    @Test fun `version 48 repairs contribution schema without resetting debt or inventing rewards`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        connection.createStatement().use {
            it.execute("INSERT INTO guild_bank_contribution_accounts VALUES ('guild', 500, 1000)")
            it.execute("INSERT INTO guild_bank_contribution_receipts VALUES ('paid', 'guild', 0, 1000)")
            it.execute("INSERT INTO guild_bank_contribution_quests VALUES ('paid', 'bank', 'week')")
            it.execute("DROP TABLE guild_bank_contribution_guards")
            it.execute("PRAGMA user_version = 47")
        }
        repeat(2) { SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate() }
        assertEquals(BankContributionSchema.VERSION, databaseVersion())
        assertTrue(tableExists(BankContributionSchema.GUARDS))
        connection.createStatement().use { s ->
            s.executeQuery("SELECT withdrawal_debt, fresh_total FROM guild_bank_contribution_accounts").use {
                assertTrue(it.next()); assertEquals(500, it.getLong(1)); assertEquals(1000, it.getLong(2))
            }
            s.executeQuery("SELECT COUNT(*) FROM guild_bank_contribution_quests").use {
                assertTrue(it.next()); assertEquals(1, it.getInt(1))
            }
            s.executeQuery("SELECT COUNT(*) FROM guild_bank_xp_deliveries").use {
                assertTrue(it.next()); assertEquals(0, it.getInt(1))
            }
        }
    }

    @Test fun `version 49 adds recovery tables without fabricating old intents`() {
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        connection.createStatement().use {
            it.execute("INSERT INTO guild_bank_contribution_receipts VALUES ('old', 'guild', 0, 1000)")
            it.execute("DROP TABLE guild_bank_quest_intents")
            it.execute("DROP TABLE guild_bank_quest_recovery")
            it.execute("DROP TABLE guild_bank_quest_attempts")
            it.execute("DROP TABLE guild_bank_quest_transition")
            it.execute("DROP TABLE guild_bank_quest_revision")
            it.execute("PRAGMA user_version = 48")
        }
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        connection.createStatement().use { it.execute("UPDATE guild_bank_quest_revision SET revision = 7 WHERE id = 1") }
        SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        assertEquals(BankContributionSchema.VERSION, databaseVersion())
        assertTrue(tableExists(BankContributionSchema.QUEST_INTENTS))
        assertTrue(tableExists(BankContributionSchema.QUEST_RECOVERY))
        assertTrue(tableExists(BankContributionSchema.QUEST_ATTEMPTS))
        assertTrue(tableExists(BankContributionSchema.QUEST_TRANSITION))
        assertTrue(tableExists(BankContributionSchema.QUEST_REVISION))
        connection.createStatement().use { s ->
            s.executeQuery("SELECT revision FROM guild_bank_quest_revision WHERE id = 1").use {
                assertTrue(it.next()); assertEquals(7, it.getInt(1))
            }
            s.executeQuery("SELECT COUNT(*) FROM guild_bank_quest_intents").use {
                assertTrue(it.next()); assertEquals(0, it.getInt(1))
            }
        }
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
