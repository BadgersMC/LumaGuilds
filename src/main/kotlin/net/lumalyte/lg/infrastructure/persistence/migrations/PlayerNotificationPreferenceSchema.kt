package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object PlayerNotificationPreferenceSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS player_notification_preferences (
                    player_id VARCHAR(36) PRIMARY KEY,
                    guild_login_enabled INTEGER NOT NULL DEFAULT 1,
                    updated_at BIGINT NOT NULL
                )$engine
                """.trimIndent()
            )
        }
    }
}
