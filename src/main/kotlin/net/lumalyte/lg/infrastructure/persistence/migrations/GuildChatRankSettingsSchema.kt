package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object GuildChatRankSettingsSchema {
    const val TABLE = "guild_chat_rank_settings"
    fun create(connection: Connection, mariaDb: Boolean) {
        val idType = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use {
            it.executeUpdate("""CREATE TABLE IF NOT EXISTS $TABLE (
                guild_id $idType PRIMARY KEY,
                ranks_visible INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )$engine""".trimIndent())
        }
    }
}
