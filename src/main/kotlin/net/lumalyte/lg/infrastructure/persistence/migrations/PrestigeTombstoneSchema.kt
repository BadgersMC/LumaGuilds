package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection
import java.time.Instant
import java.util.UUID

/**
 * REQ-163: the name and tier of each prestiged guild at the moment it was
 * disbanded. Prestige receipts and the gold journal are kept by guild id, so
 * this is what lets history and server-firsts still name the guild.
 */
internal object PrestigeTombstoneSchema {
    const val VERSION = 50
    const val TABLE = "guild_prestige_tombstones"

    fun create(connection: Connection, mariaDb: Boolean) {
        val text = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val name = if (mariaDb) "VARCHAR(255)" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use {
            it.executeUpdate(
                "CREATE TABLE IF NOT EXISTS $TABLE (" +
                    "guild_id $text PRIMARY KEY, " +
                    "name $name NOT NULL, " +
                    "prestige_count INTEGER NOT NULL, " +
                    "disbanded_at BIGINT NOT NULL" +
                    ")$engine"
            )
        }
    }

    /**
     * Records [guildId] if it has prestiged. Runs inside the disband transaction,
     * before the guild and its reward account are deleted, so a failed disband
     * leaves nothing behind.
     */
    fun recordIfPrestiged(connection: Connection, guildId: UUID, disbandedAt: Instant) {
        val id = guildId.toString()
        val prestigeCount = connection.prepareStatement(
            "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?"
        ).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
        if (prestigeCount <= 0) return
        val name = connection.prepareStatement("SELECT name FROM guilds WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        } ?: return
        connection.prepareStatement(
            "INSERT INTO $TABLE (guild_id, name, prestige_count, disbanded_at) VALUES (?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, name)
            statement.setInt(3, prestigeCount)
            statement.setLong(4, disbandedAt.toEpochMilli())
            statement.executeUpdate()
        }
    }
}
