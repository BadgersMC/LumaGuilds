package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class GuildStrikeFeedSchemaTest {
    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use {
            it.execute(
                """
                CREATE TABLE guild_strikes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guild_id TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `migration adds provider identity and expiration schema idempotently`() {
        GuildStrikeFeedSchema.migrate(connection)
        GuildStrikeFeedSchema.migrate(connection)

        assertTrue(columnExists("guild_strikes", "source_provider"))
        assertTrue(columnExists("guild_strikes", "source_punishment_id"))
        assertTrue(columnExists("guild_strikes", "expires_at"))
        assertTrue(indexExists("guild_strikes", "idx_guild_strikes_source"))
    }

    private fun columnExists(table: String, column: String): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                generateSequence { if (rows.next()) rows.getString("name") else null }
                    .any { it == column }
            }
        }

    private fun indexExists(table: String, index: String): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA index_list($table)").use { rows ->
                generateSequence { if (rows.next()) rows.getString("name") else null }
                    .any { it == index }
            }
        }
}
