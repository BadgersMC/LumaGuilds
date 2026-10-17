package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.QuestCompletionNotificationRepository
import net.lumalyte.lg.domain.entities.QuestCompletionNotification
import net.lumalyte.lg.infrastructure.persistence.migrations.QuestCompletionNotificationSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.UUID

class QuestCompletionNotificationRepositorySQL(
    private val storage: Storage<Database>,
) : QuestCompletionNotificationRepository {

    override fun add(notification: QuestCompletionNotification): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO ${QuestCompletionNotificationSchema.TABLE} (
                    notification_id, player_id, week_id, quest_id,
                    guild_id, created_at, delivered_at
                ) VALUES (?,?,?,?,?,?,?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, notification.id.toString())
                statement.setString(2, notification.playerId.toString())
                statement.setString(3, notification.weekId)
                statement.setString(4, notification.questId)
                statement.setString(5, notification.guildId.toString())
                statement.setLong(6, notification.createdAt)
                if (notification.deliveredAt == null) {
                    statement.setNull(7, Types.BIGINT)
                } else {
                    statement.setLong(7, notification.deliveredAt)
                }
                try {
                    statement.executeUpdate() == 1
                } catch (error: SQLException) {
                    if (isDuplicateKey(error)) false else throw error
                }
            }
        }

    override fun getPending(playerId: UUID): List<QuestCompletionNotification> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT * FROM ${QuestCompletionNotificationSchema.TABLE}
                WHERE player_id = ? AND delivered_at IS NULL
                ORDER BY created_at ASC, notification_id ASC
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, playerId.toString())
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(read(rows))
                    }
                }
            }
        }

    override fun markDelivered(notificationId: UUID, deliveredAt: Long): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE ${QuestCompletionNotificationSchema.TABLE}
                SET delivered_at = ?
                WHERE notification_id = ? AND delivered_at IS NULL
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, deliveredAt)
                statement.setString(2, notificationId.toString())
                statement.executeUpdate() == 1
            }
        }

    private fun read(rows: ResultSet): QuestCompletionNotification =
        QuestCompletionNotification(
            id = UUID.fromString(rows.getString("notification_id")),
            playerId = UUID.fromString(rows.getString("player_id")),
            weekId = rows.getString("week_id"),
            questId = rows.getString("quest_id"),
            guildId = UUID.fromString(rows.getString("guild_id")),
            createdAt = rows.getLong("created_at"),
            deliveredAt = rows.getLong("delivered_at").let {
                if (rows.wasNull()) null else it
            },
        )

    private fun isDuplicateKey(error: SQLException): Boolean =
        error.errorCode in setOf(19, 1062, 1555, 2067)
}
