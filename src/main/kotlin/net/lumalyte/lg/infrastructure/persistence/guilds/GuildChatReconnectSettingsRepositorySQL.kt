package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildChatReconnectSettingsRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildChatReconnectSettingsSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** Reads durable preferences and rejects stale writes without a guild snapshot/cache. */
internal class GuildChatReconnectSettingsRepositorySQL(private val storage: Storage<Database>) :
    GuildChatReconnectSettingsRepository {
    private val logger = LoggerFactory.getLogger(GuildChatReconnectSettingsRepositorySQL::class.java)

    init {
        storage.connection.connection.use { GuildChatReconnectSettingsSchema.create(it) }
    }

    override fun resetOnJoin(guildId: UUID): Boolean {
        return try {
            storage.connection.connection.use { readPreference(it, guildId) }
        } catch (error: SQLException) {
            logger.error("Failed to read guild reconnect preference for $guildId", error)
            false
        }
    }

    private fun readPreference(connection: Connection, guildId: UUID): Boolean =
        connection.prepareStatement(READ_SQL).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { row -> row.next() && row.getBoolean("reset_on_join") }
        }

    override fun compareAndSet(guildId: UUID, expected: Boolean, enabled: Boolean): Boolean {
        return try {
            storage.connection.connection.use { connection ->
                insertDefault(connection, guildId)
                connection.prepareStatement(UPDATE_SQL).use {
                    it.setBoolean(1, enabled)
                    it.setString(2, guildId.toString())
                    it.setBoolean(EXPECTED_STATE_PARAMETER, expected)
                    it.executeUpdate() == 1
                }
            }
        } catch (error: SQLException) {
            logger.error("Failed to save guild reconnect preference for $guildId", error)
            false
        }
    }

    private fun insertDefault(connection: Connection, guildId: UUID) {
        val sql = if (storage.dialect == SqlDialect.MARIADB) INSERT_MARIA else INSERT_SQLITE
        connection.prepareStatement(sql).use {
            it.setString(1, guildId.toString())
            it.executeUpdate()
        }
    }

    private companion object {
        const val EXPECTED_STATE_PARAMETER = 3
        const val READ_SQL = "SELECT reset_on_join FROM guild_chat_reconnect_settings WHERE guild_id = ?"
        const val UPDATE_SQL =
            "UPDATE guild_chat_reconnect_settings SET reset_on_join = ? WHERE guild_id = ? AND reset_on_join = ?"
        const val INSERT_SQLITE =
            "INSERT INTO guild_chat_reconnect_settings (guild_id, reset_on_join) VALUES (?, FALSE) " +
                "ON CONFLICT(guild_id) DO NOTHING"
        const val INSERT_MARIA =
            "INSERT INTO guild_chat_reconnect_settings (guild_id, reset_on_join) VALUES (?, FALSE) " +
                "ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)"
    }
}
