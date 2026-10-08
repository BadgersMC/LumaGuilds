package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection
import java.util.UUID

/** Additive guild preference schema shared by startup and migration. */
internal object GuildChatReconnectSettingsSchema {
    /** Fixed table identifier shared with the database migration inventory. */
    const val TABLE = "guild_chat_reconnect_settings"
    private const val CREATE_SQL =
        "CREATE TABLE IF NOT EXISTS guild_chat_reconnect_settings " +
            "(guild_id VARCHAR(36) PRIMARY KEY, reset_on_join BOOLEAN NOT NULL DEFAULT FALSE)"

    /** Existing and new guilds remain disabled until explicitly configured. */
    fun create(connection: Connection) {
        connection.createStatement().use { it.execute(CREATE_SQL) }
    }

    /** Called inside the guild deletion transaction. */
    fun delete(connection: Connection, guildId: UUID) {
        connection.prepareStatement("DELETE FROM guild_chat_reconnect_settings WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeUpdate()
        }
    }
}
