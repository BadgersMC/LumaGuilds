package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

internal object GuildStrikeFeedSchema {
    private const val TABLE = "guild_strikes"

    fun migrate(connection: Connection) {
        addColumnIfMissing(connection, TABLE, "source_provider", "VARCHAR(32)")
        addColumnIfMissing(connection, TABLE, "source_punishment_id", "VARCHAR(128)")
        addColumnIfMissing(connection, TABLE, "expires_at", "BIGINT")

        if (!indexExists(connection, "guild_strikes", "idx_guild_strikes_source")) {
            connection.createStatement().use {
                it.execute(
                    "CREATE UNIQUE INDEX idx_guild_strikes_source " +
                        "ON guild_strikes(source_provider, source_punishment_id)",
                )
            }
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
                if (indexNames(rows).any { index.equals(it, ignoreCase = true) }) return true
            }
        }
        return false
    }

    private fun indexNames(rows: java.sql.ResultSet): Sequence<String> = generateSequence {
        if (rows.next()) rows.getString("INDEX_NAME") else null
    }
}
