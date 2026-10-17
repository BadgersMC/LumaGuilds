package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.WarNotificationRepository
import net.lumalyte.lg.domain.entities.WarNotification
import net.lumalyte.lg.domain.entities.WarNotificationKind
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.UUID

class WarNotificationRepositorySQL(
    private val storage: Storage<Database>,
) : WarNotificationRepository {

    override fun add(notification: WarNotification): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO war_notifications (
                    notification_id, player_id, kind, event_id, own_guild_id,
                    opponent_guild_id, opponent_name, opponent_banner,
                    duration_seconds, objective_count, objective_description,
                    wager_amount, terms, expires_at, own_kills, opponent_kills,
                    created_at, delivered_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent()
            ).use { statement ->
                bind(statement, notification)
                try {
                    statement.executeUpdate() == 1
                } catch (error: SQLException) {
                    if (isDuplicateKey(error)) false else throw error
                }
            }
        }

    override fun getPending(playerId: UUID): List<WarNotification> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT * FROM war_notifications
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
                UPDATE war_notifications
                SET delivered_at = ?
                WHERE notification_id = ? AND delivered_at IS NULL
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, deliveredAt)
                statement.setString(2, notificationId.toString())
                statement.executeUpdate() == 1
            }
        }

    private fun bind(statement: PreparedStatement, value: WarNotification) {
        statement.setString(1, value.id.toString())
        statement.setString(2, value.playerId.toString())
        statement.setString(3, value.kind.name)
        statement.setString(4, value.eventId.toString())
        statement.setString(5, value.ownGuildId.toString())
        statement.setString(6, value.opponentGuildId.toString())
        statement.setString(7, value.opponentName)
        statement.setString(8, value.opponentBanner)
        statement.setLong(9, value.durationSeconds)
        statement.setInt(10, value.objectiveCount)
        statement.setString(11, value.objectiveDescription)
        statement.setInt(12, value.wagerAmount)
        statement.setString(13, value.terms)
        setNullableLong(statement, 14, value.expiresAt)
        setNullableInt(statement, 15, value.ownKills)
        setNullableInt(statement, 16, value.opponentKills)
        statement.setLong(17, value.createdAt)
        setNullableLong(statement, 18, value.deliveredAt)
    }

    private fun read(rows: ResultSet): WarNotification =
        WarNotification(
            id = UUID.fromString(rows.getString("notification_id")),
            playerId = UUID.fromString(rows.getString("player_id")),
            kind = WarNotificationKind.valueOf(rows.getString("kind")),
            eventId = UUID.fromString(rows.getString("event_id")),
            ownGuildId = UUID.fromString(rows.getString("own_guild_id")),
            opponentGuildId = UUID.fromString(rows.getString("opponent_guild_id")),
            opponentName = rows.getString("opponent_name"),
            opponentBanner = rows.getString("opponent_banner"),
            durationSeconds = rows.getLong("duration_seconds"),
            objectiveCount = rows.getInt("objective_count"),
            objectiveDescription = rows.getString("objective_description"),
            wagerAmount = rows.getInt("wager_amount"),
            terms = rows.getString("terms"),
            expiresAt = nullableLong(rows, "expires_at"),
            ownKills = nullableInt(rows, "own_kills"),
            opponentKills = nullableInt(rows, "opponent_kills"),
            createdAt = rows.getLong("created_at"),
            deliveredAt = nullableLong(rows, "delivered_at"),
        )

    private fun setNullableLong(statement: PreparedStatement, index: Int, value: Long?) {
        if (value == null) statement.setNull(index, Types.BIGINT) else statement.setLong(index, value)
    }

    private fun setNullableInt(statement: PreparedStatement, index: Int, value: Int?) {
        if (value == null) statement.setNull(index, Types.INTEGER) else statement.setInt(index, value)
    }

    private fun nullableLong(rows: ResultSet, column: String): Long? =
        rows.getLong(column).let { if (rows.wasNull()) null else it }

    private fun nullableInt(rows: ResultSet, column: String): Int? =
        rows.getInt(column).let { if (rows.wasNull()) null else it }

    private fun isDuplicateKey(error: SQLException): Boolean =
        error.errorCode in setOf(19, 1062, 1555, 2067)
}
