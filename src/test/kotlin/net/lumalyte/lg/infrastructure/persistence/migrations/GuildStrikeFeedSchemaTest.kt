package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.properties.Delegates

/** Checks additive provider columns and an idempotent unique source index. */
internal class GuildStrikeFeedSchemaTest {
    private var connection: Connection by Delegates.notNull()

    /** Initialize a legacy SQLite strike schema. */
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

    /** Close the isolated database. */
    @AfterEach
    fun tearDown() {
        connection.close()
    }

    /** Repeat migration without losing provider identity or expiration columns. */
    @DisplayName("migration adds provider identity and expiration schema idempotently")
    @Test
    fun additiveMigrationIdempotent() {
        GuildStrikeFeedSchema.migrate(connection)
        GuildStrikeFeedSchema.migrate(connection)

        assertTrue(columnExists(TABLE, "source_provider"))
        assertTrue(columnExists(TABLE, "source_punishment_id"))
        assertTrue(columnExists(TABLE, "expires_at"))
        assertTrue(indexExists(TABLE, "idx_guild_strikes_source"))
    }

    private fun columnExists(table: String, column: String): Boolean {
        return connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                generateSequence { if (rows.next()) rows.getString("name") else null }
                    .any { it == column }
            }
        }
    }

    private fun indexExists(table: String, index: String): Boolean {
        return connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA index_list($table)").use { rows ->
                generateSequence { if (rows.next()) rows.getString("name") else null }
                    .any { it == index }
            }
        }
    }

    private companion object {
        const val TABLE = "guild_strikes"
    }
}
