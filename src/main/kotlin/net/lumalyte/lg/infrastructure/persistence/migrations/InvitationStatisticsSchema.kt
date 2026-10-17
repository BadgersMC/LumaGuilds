package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object InvitationStatisticsSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        connection.createStatement().use { statement ->
            statement.execute(if (mariaDb) mariaSql else sqliteSql)
            if (!mariaDb) {
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_guild_invite_history_guild " +
                        "ON guild_invitation_history(guild_id)"
                )
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_guild_invite_history_inviter " +
                        "ON guild_invitation_history(guild_id, inviter_player_id)"
                )
            }
        }
    }

    private val sqliteSql = """
        CREATE TABLE IF NOT EXISTS guild_invitation_history (
            id TEXT PRIMARY KEY,
            guild_id TEXT NOT NULL,
            inviter_player_id TEXT NOT NULL,
            invited_player_id TEXT NOT NULL,
            sent_at INTEGER NOT NULL,
            FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
        )
    """.trimIndent()
    private val mariaSql = """
        CREATE TABLE IF NOT EXISTS guild_invitation_history (
            id VARCHAR(36) PRIMARY KEY,
            guild_id VARCHAR(36) NOT NULL,
            inviter_player_id VARCHAR(36) NOT NULL,
            invited_player_id VARCHAR(36) NOT NULL,
            sent_at BIGINT NOT NULL,
            FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
            INDEX idx_guild_invite_history_guild (guild_id),
            INDEX idx_guild_invite_history_inviter (guild_id, inviter_player_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()
}
