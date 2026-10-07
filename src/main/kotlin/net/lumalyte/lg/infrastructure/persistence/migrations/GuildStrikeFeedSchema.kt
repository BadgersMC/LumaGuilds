package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

internal object GuildStrikeFeedSchema {
    fun migrate(connection: Connection) {
        addColumnIfMissing(connection, "guild_strikes", "source_provider", "VARCHAR(32)")
        addColumnIfMissing(connection, "guild_strikes", "source_punishment_id", "VARCHAR(128)")
        addColumnIfMissing(connection, "guild_strikes", "expires_at", "BIGINT")

        if (!indexExists(connection, "guild_strikes", "idx_guild_strikes_source")) {
            connection.createStatement().use {
                it.execute(
                    "CREATE UNIQUE INDEX idx_guild_strikes_source " +
                        "ON guild_strikes(source_provider, source_punishment_id)",
                )
            }
        }
        connection.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_strike_feed_cursors (
                    provider VARCHAR(32) NOT NULL PRIMARY KEY,
                    occurred_at VARCHAR(64) NOT NULL,
                    event_id VARCHAR(36) NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun addColumnIfMissing(connection: Connection, table: String, column: String, definition: String) {
        if (columnExists(connection, table, column)) return
        connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
    }

    private fun columnExists(connection: Connection, table: String, column: String): Boolean {
        for (tablePattern in listOf(table, table.uppercase())) {
            connection.metaData.getColumns(connection.catalog, null, tablePattern, column).use { rows ->
                if (rows.next()) return true
            }
        }
        return false
    }

    private fun indexExists(connection: Connection, table: String, index: String): Boolean {
        for (tablePattern in listOf(table, table.uppercase())) {
            connection.metaData.getIndexInfo(connection.catalog, null, tablePattern, false, false).use { rows ->
                while (rows.next()) {
                    if (index.equals(rows.getString("INDEX_NAME"), ignoreCase = true)) return true
                }
            }
        }
        return false
    }
}
