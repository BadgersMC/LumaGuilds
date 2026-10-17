package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection
import java.util.UUID

object ChronicleGoalSchema {
    const val VERSION = 46

    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB" else ""
        connection.createStatement().use { it.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_chronicle_goals (" +
                "guild_id VARCHAR(36) PRIMARY KEY, reward_id VARCHAR(64), revision BIGINT NOT NULL)$engine",
        ) }
    }

    fun delete(connection: Connection, guild: UUID) {
        connection.prepareStatement("DELETE FROM guild_chronicle_goals WHERE guild_id = ?").use {
            it.setString(1, guild.toString())
            it.executeUpdate()
        }
    }
}
