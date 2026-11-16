package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/**
 * REQ-273: MariaDB installs were given the first `relations` layout (guild_id, target_guild_id,
 * relation_type) while RelationRepositorySQLite reads the current one (id, guild_a, guild_b, type,
 * status, ...), so alliances, enemies and truces could not load there. This moves an old-layout
 * table aside, creates the current layout and carries the old rows across. It is idempotent and
 * does nothing once the table has an `id` column.
 */
internal object RelationsSchemaRepair {
    const val LEGACY_TABLE = "relations_legacy_v1"

    fun ensure(connection: Connection) {
        val columns = columns(connection, "relations") ?: return create(connection)
        if ("id" in columns) return
        if ("guild_id" !in columns || "target_guild_id" !in columns) return
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS $LEGACY_TABLE")
            statement.execute("RENAME TABLE relations TO $LEGACY_TABLE")
        }
        create(connection)
        connection.createStatement().use { statement ->
            statement.execute(
                """INSERT IGNORE INTO relations (id, guild_a, guild_b, type, status, expires_at, requesting_guild, created_at, updated_at)
                   SELECT UUID(), LEAST(guild_id, target_guild_id), GREATEST(guild_id, target_guild_id),
                          UPPER(relation_type), 'ACTIVE', NULL, NULL,
                          DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%sZ'), DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%sZ')
                   FROM $LEGACY_TABLE
                   WHERE guild_id <> target_guild_id AND UPPER(relation_type) IN ('ALLY', 'ENEMY', 'TRUCE', 'NEUTRAL')""",
            )
        }
    }

    private fun create(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """CREATE TABLE IF NOT EXISTS relations (
                       id VARCHAR(36) NOT NULL PRIMARY KEY,
                       guild_a VARCHAR(36) NOT NULL,
                       guild_b VARCHAR(36) NOT NULL,
                       type VARCHAR(20) NOT NULL,
                       status VARCHAR(20) NOT NULL,
                       expires_at VARCHAR(40) NULL,
                       requesting_guild VARCHAR(36) NULL,
                       created_at VARCHAR(40) NOT NULL,
                       updated_at VARCHAR(40) NOT NULL,
                       UNIQUE KEY uq_relations_pair (guild_a, guild_b),
                       CHECK (guild_a < guild_b)
                   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""",
            )
        }
    }

    private fun columns(connection: Connection, table: String): Set<String>? =
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM $table WHERE 1 = 0").use { rows ->
                    val metadata = rows.metaData
                    (1..metadata.columnCount).map { metadata.getColumnLabel(it).lowercase() }.toSet()
                }
            }
        } catch (_: java.sql.SQLException) {
            null
        }
}
