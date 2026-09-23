package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object GuildDiscordRoleSchema {
    const val TABLE = "guild_discord_roles"

    fun create(connection: Connection, mariaDb: Boolean) {
        val sql = if (mariaDb) {
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                guild_id VARCHAR(36) NOT NULL PRIMARY KEY,
                discord_role_id VARCHAR(30) NOT NULL,
                unlocked_at BIGINT NOT NULL
            )
            """.trimIndent()
        } else {
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                guild_id TEXT NOT NULL PRIMARY KEY,
                discord_role_id TEXT NOT NULL,
                unlocked_at INTEGER NOT NULL
            )
            """.trimIndent()
        }
        connection.createStatement().use { it.executeUpdate(sql) }
    }
}
