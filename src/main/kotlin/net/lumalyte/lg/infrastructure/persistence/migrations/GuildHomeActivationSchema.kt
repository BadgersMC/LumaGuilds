package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection
import java.sql.SQLException

object GuildHomeActivationSchema {
    const val ACTIVATIONS_TABLE = "guild_home_activations"
    const val CREDITS_TABLE = "guild_home_activation_credits"

    fun create(connection: Connection, mariaDb: Boolean) {
        val idType = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val nameType = if (mariaDb) "VARCHAR(64)" else "TEXT"
        val integer = if (mariaDb) "BIGINT" else "INTEGER"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS $ACTIVATIONS_TABLE (
                    guild_id $idType NOT NULL,
                    home_name $nameType NOT NULL,
                    activated_at $integer NOT NULL,
                    transaction_id $idType,
                    PRIMARY KEY (guild_id, home_name),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )$engine
            """.trimIndent())
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS $CREDITS_TABLE (
                    guild_id $idType PRIMARY KEY,
                    credits INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )$engine
            """.trimIndent())
        }
    }

    /**
     * Preserve successful Chapter 2 activation payments made before activation state existed.
     * Each uncompensated applied debit becomes one credit that can activate one saved legacy home.
     */
    fun backfillLegacyCredits(connection: Connection, mariaDb: Boolean) {
        val rows: List<Pair<String, Int>> = try {
            connection.createStatement().use { statement ->
                statement.executeQuery("""
                    SELECT guild_id,
                        SUM(CASE WHEN direction = 'DEBIT' AND status = 'APPLIED'
                            AND description LIKE 'Activate guild home #%'
                            THEN 1 ELSE 0 END) AS applied_count,
                        SUM(CASE WHEN direction = 'CREDIT' AND status = 'APPLIED'
                            AND description = 'Compensate failed guild home activation'
                            THEN 1 ELSE 0 END) AS compensation_count
                    FROM guild_gold_operations
                    GROUP BY guild_id
                """.trimIndent()).use { result ->
                    val creditsByGuild = mutableListOf<Pair<String, Int>>()
                    while (result.next()) {
                        val credits = (result.getInt("applied_count") - result.getInt("compensation_count")).coerceAtLeast(0)
                        if (credits > 0) creditsByGuild += result.getString("guild_id") to credits
                    }
                    creditsByGuild
                }
            }
        } catch (_: SQLException) {
            return
        }
        if (rows.isEmpty()) return
        val sql = if (mariaDb) {
            "INSERT INTO $CREDITS_TABLE (guild_id, credits) VALUES (?, ?) ON DUPLICATE KEY UPDATE credits = VALUES(credits)"
        } else {
            "INSERT INTO $CREDITS_TABLE (guild_id, credits) VALUES (?, ?) ON CONFLICT(guild_id) DO UPDATE SET credits = excluded.credits"
        }
        connection.prepareStatement(sql).use { statement ->
            rows.forEach { (guildId, credits) ->
                statement.setString(1, guildId)
                statement.setInt(2, credits)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
