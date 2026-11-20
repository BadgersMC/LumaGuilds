package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection
import java.sql.SQLException

/** Additive, idempotent schema: older binaries can ignore this nullable column. */
internal object GuildDescriptionSchema {
    fun ensure(connection: Connection) {
        if (hasDescription(connection)) return
        try {
            connection.createStatement().use { it.execute("ALTER TABLE guilds ADD COLUMN description TEXT") }
        } catch (failure: SQLException) {
            // Another initializer may have added it between the read and the ALTER.
            if (!hasDescription(connection)) throw failure
        }
        if (!hasDescription(connection)) throw SQLException("Guild description column is unavailable")
    }

    private fun hasDescription(connection: Connection): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM guilds WHERE 1 = 0").use { rows ->
                val metadata = rows.metaData
                (1..metadata.columnCount).any { metadata.getColumnLabel(it).equals("description", ignoreCase = true) }
            }
        }
}
