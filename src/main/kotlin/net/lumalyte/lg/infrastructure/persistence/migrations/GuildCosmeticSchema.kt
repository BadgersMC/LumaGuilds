package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.domain.entities.MAX_COSMETIC_DISPLAY_NAME_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_KEY_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_SOURCE_LENGTH
import net.lumalyte.lg.domain.entities.MAX_COSMETIC_TYPE_LENGTH
import java.sql.Connection

/** Additive schema for durable guild cosmetic ownership (REQ-121). */
object GuildCosmeticSchema {
    /** Ensures the cosmetic ownership ledger exists for the active SQL dialect. */
    fun create(connection: Connection, mariaDb: Boolean) {
        val guildIdType = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val typeType = if (mariaDb) "VARCHAR($MAX_COSMETIC_TYPE_LENGTH)" else "TEXT"
        val keyType = if (mariaDb) "VARCHAR($MAX_COSMETIC_KEY_LENGTH)" else "TEXT"
        val displayNameType = if (mariaDb) "VARCHAR($MAX_COSMETIC_DISPLAY_NAME_LENGTH)" else "TEXT"
        val sourceType = if (mariaDb) "VARCHAR($MAX_COSMETIC_SOURCE_LENGTH)" else "TEXT"
        val timestampType = if (mariaDb) "BIGINT" else "INTEGER"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                    CREATE TABLE IF NOT EXISTS guild_cosmetic_unlocks (
                        guild_id $guildIdType NOT NULL,
                        cosmetic_type $typeType NOT NULL,
                        cosmetic_key $keyType NOT NULL,
                        display_name $displayNameType NOT NULL,
                        source $sourceType NOT NULL,
                        unlocked_at $timestampType NOT NULL,
                        PRIMARY KEY (guild_id, cosmetic_type, cosmetic_key)
                    )$engine
                """.trimIndent(),
            )
        }
    }
}
