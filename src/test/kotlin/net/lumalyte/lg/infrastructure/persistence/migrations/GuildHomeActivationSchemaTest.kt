package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildHomeActivationSchemaTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `legacy saved homes remain inactive and successful chapter two payments become credits`() {
        DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("home-activation.db")}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY)")
                statement.execute("CREATE TABLE guild_homes (guild_id TEXT, name TEXT)")
                statement.execute("""
                    CREATE TABLE guild_gold_operations (
                        transaction_id TEXT PRIMARY KEY,
                        guild_id TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        status TEXT NOT NULL,
                        description TEXT
                    )
                """.trimIndent())
                statement.execute("INSERT INTO guilds (id) VALUES ('legacy'), ('paid'), ('compensated')")
                statement.execute("INSERT INTO guild_homes (guild_id, name) VALUES ('legacy','main'), ('paid','main'), ('compensated','main')")
                statement.execute("INSERT INTO guild_gold_operations VALUES ('p1','paid','DEBIT','APPLIED','Activate guild home #1')")
                statement.execute("INSERT INTO guild_gold_operations VALUES ('c1','compensated','DEBIT','APPLIED','Activate guild home #1')")
                statement.execute("INSERT INTO guild_gold_operations VALUES ('c2','compensated','CREDIT','APPLIED','Compensate failed guild home activation')")
            }

            GuildHomeActivationSchema.create(connection, mariaDb = false)
            GuildHomeActivationSchema.backfillLegacyCredits(connection, mariaDb = false)

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM ${GuildHomeActivationSchema.ACTIVATIONS_TABLE}").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1), "saved Season 1 locations must not be auto-activated")
                }
                statement.executeQuery("SELECT credits FROM ${GuildHomeActivationSchema.CREDITS_TABLE} WHERE guild_id='paid'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                }
                statement.executeQuery("SELECT 1 FROM ${GuildHomeActivationSchema.CREDITS_TABLE} WHERE guild_id='compensated'").use { rows ->
                    assertFalse(rows.next(), "compensated activation must not create a reusable credit")
                }
            }
        }
    }
}
