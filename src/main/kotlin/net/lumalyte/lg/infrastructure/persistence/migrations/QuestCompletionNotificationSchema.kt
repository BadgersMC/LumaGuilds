package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object QuestCompletionNotificationSchema {
    const val TABLE = "quest_completion_notifications"

    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS $TABLE (
                    notification_id VARCHAR(36) PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    week_id VARCHAR(255) NOT NULL,
                    quest_id VARCHAR(255) NOT NULL,
                    guild_id VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    delivered_at BIGINT
                )$engine
                """.trimIndent()
            )
            if (!mariaDb) {
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_quest_completion_notifications_pending " +
                        "ON $TABLE(player_id, delivered_at, created_at)"
                )
            }
        }
        if (mariaDb) createMariaIndex(connection)
    }

    private fun createMariaIndex(connection: Connection) {
        val exists = connection.metaData.getIndexInfo(
            null, null, TABLE, false, false,
        ).use { rows ->
            generateSequence { if (rows.next()) rows.getString("INDEX_NAME") else null }
                .any {
                    it.equals(
                        "idx_quest_completion_notifications_pending",
                        ignoreCase = true,
                    )
                }
        }
        if (!exists) {
            connection.createStatement().use {
                it.execute(
                    "CREATE INDEX idx_quest_completion_notifications_pending " +
                        "ON $TABLE(player_id, delivered_at, created_at)"
                )
            }
        }
    }
}
