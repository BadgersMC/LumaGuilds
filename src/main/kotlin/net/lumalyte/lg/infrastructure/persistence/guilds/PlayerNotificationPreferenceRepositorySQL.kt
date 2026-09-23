package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.PlayerNotificationPreferenceRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

class PlayerNotificationPreferenceRepositorySQL(
    private val storage: Storage<Database>,
) : PlayerNotificationPreferenceRepository {

    override fun isGuildLoginEnabled(playerId: UUID): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT guild_login_enabled
                FROM player_notification_preferences
                WHERE player_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, playerId.toString())
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getBoolean(1) else true
                }
            }
        }
    override fun guildLoginStates(playerIds: Set<UUID>): Map<UUID, Boolean> {
        if (playerIds.isEmpty()) return emptyMap()
        return storage.connection.connection.use { connection ->
            val placeholders = List(playerIds.size) { "?" }.joinToString(",")
            connection.prepareStatement(
                """
                SELECT player_id, guild_login_enabled
                FROM player_notification_preferences
                WHERE player_id IN ($placeholders)
                """.trimIndent()
            ).use { statement ->
                playerIds.forEachIndexed { index, playerId ->
                    statement.setString(index + 1, playerId.toString())
                }
                val stored = buildMap {
                    statement.executeQuery().use { rows ->
                        while (rows.next()) {
                            put(
                                UUID.fromString(rows.getString("player_id")),
                                rows.getBoolean("guild_login_enabled"),
                            )
                        }
                    }
                }
                playerIds.associateWith { stored[it] ?: true }
            }
        }
    }

    override fun setGuildLoginEnabled(playerId: UUID, enabled: Boolean): Boolean =
        storage.connection.connection.use { connection ->
            val updatedAt = System.currentTimeMillis()
            if (update(connection, playerId, enabled, updatedAt)) {
                return@use true
            }
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO player_notification_preferences (
                        player_id, guild_login_enabled, updated_at
                    ) VALUES (?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setBoolean(2, enabled)
                    statement.setLong(3, updatedAt)
                    statement.executeUpdate() == 1
                }
            } catch (error: SQLException) {
                if (!isDuplicateKey(error)) throw error
                update(connection, playerId, enabled, updatedAt)
            }
        }
    private fun update(
        connection: Connection,
        playerId: UUID,
        enabled: Boolean,
        updatedAt: Long,
    ): Boolean =
        connection.prepareStatement(
            """
            UPDATE player_notification_preferences
            SET guild_login_enabled = ?, updated_at = ?
            WHERE player_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setBoolean(1, enabled)
            statement.setLong(2, updatedAt)
            statement.setString(3, playerId.toString())
            statement.executeUpdate() == 1
        }

    private fun isDuplicateKey(error: SQLException): Boolean =
        error.errorCode in setOf(19, 1062, 1555, 2067)
}
