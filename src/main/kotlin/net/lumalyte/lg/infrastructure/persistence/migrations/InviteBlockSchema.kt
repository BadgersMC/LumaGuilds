package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/** Personal invite blocks (v45): [owner_id] blocked [target_id] from inviting them and from their guild chats. */
object InviteBlockSchema {
    const val TABLE = "player_invite_blocks"
    fun create(connection: Connection, mariaDb: Boolean) {
        val idType = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use {
            it.executeUpdate("""CREATE TABLE IF NOT EXISTS $TABLE (
                owner_id $idType NOT NULL,
                target_id $idType NOT NULL,
                created_at BIGINT NOT NULL,
                PRIMARY KEY (owner_id, target_id)
            )$engine""".trimIndent())
        }
    }
}
