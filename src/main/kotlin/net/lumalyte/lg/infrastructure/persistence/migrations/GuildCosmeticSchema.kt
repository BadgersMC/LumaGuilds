package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.domain.entities.MAX_COSMETIC_DISPLAY_NAME_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_KEY_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_SOURCE_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_TYPE_LENGTH
import java.sql.Connection

/** Additive schema for durable guild cosmetic ownership (REQ-121). */
object GuildCosmeticSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        connection.createStatement().use { statement ->
            statement.execute(if (mariaDb) mariaSql else sqliteSql)
        }
    }

    private val mariaSql = """
        CREATE TABLE IF NOT EXISTS guild_cosmetic_unlocks (
            guild_id VARCHAR(36) NOT NULL,
            cosmetic_type VARCHAR($MAX_COSMETIC_TYPE_LENGTH) NOT NULL,
            cosmetic_key VARCHAR($MAX_COSMETIC_KEY_LENGTH) NOT NULL,
            display_name VARCHAR($MAX_COSMETIC_DISPLAY_NAME_LENGTH) NOT NULL,
            source VARCHAR($MAX_COSMETIC_SOURCE_LENGTH) NOT NULL,
            unlocked_at BIGINT NOT NULL,
            PRIMARY KEY (guild_id, cosmetic_type, cosmetic_key)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private val sqliteSql = """
        CREATE TABLE IF NOT EXISTS guild_cosmetic_unlocks (
            guild_id TEXT NOT NULL,
            cosmetic_type TEXT NOT NULL,
            cosmetic_key TEXT NOT NULL,
            display_name TEXT NOT NULL,
            source TEXT NOT NULL,
            unlocked_at INTEGER NOT NULL,
            PRIMARY KEY (guild_id, cosmetic_type, cosmetic_key)
        )
    """.trimIndent()
}
