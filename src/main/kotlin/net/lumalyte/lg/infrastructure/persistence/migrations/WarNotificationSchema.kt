package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object WarNotificationSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val text = if (mariaDb) "LONGTEXT" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS war_notifications (
                    notification_id VARCHAR(36) PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    kind VARCHAR(48) NOT NULL,
                    event_id VARCHAR(36) NOT NULL,
                    own_guild_id VARCHAR(36) NOT NULL,
                    opponent_guild_id VARCHAR(36) NOT NULL,
                    opponent_name VARCHAR(128) NOT NULL,
                    opponent_banner $text,
                    duration_seconds BIGINT NOT NULL DEFAULT 0,
                    objective_count INTEGER NOT NULL DEFAULT 0,
                    objective_description $text,
                    wager_amount INTEGER NOT NULL DEFAULT 0,
                    terms $text,
                    expires_at BIGINT,
                    own_kills INTEGER,
                    opponent_kills INTEGER,
                    created_at BIGINT NOT NULL,
                    delivered_at BIGINT
                )$engine
                """.trimIndent()
            )
            if (!mariaDb) {
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_war_notifications_pending " +
                        "ON war_notifications(player_id, delivered_at, created_at)"
                )
            }
        }
        if (mariaDb) createMariaIndex(connection)
    }

    private fun createMariaIndex(connection: Connection) {
        val exists = connection.metaData.getIndexInfo(
            null, null, "war_notifications", false, false,
        ).use { rows ->
            generateSequence { if (rows.next()) rows.getString("INDEX_NAME") else null }
                .any { it.equals("idx_war_notifications_pending", ignoreCase = true) }
        }
        if (!exists) {
            connection.createStatement().use {
                it.execute(
                    "CREATE INDEX idx_war_notifications_pending " +
                        "ON war_notifications(player_id, delivered_at, created_at)"
                )
            }
        }
    }
}
