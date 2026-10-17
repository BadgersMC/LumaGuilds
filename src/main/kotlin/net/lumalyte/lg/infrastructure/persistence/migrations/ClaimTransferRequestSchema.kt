package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object ClaimTransferRequestSchema {
    const val TABLE = "claim_transfer_requests"

    fun create(connection: Connection, mariaDb: Boolean) {
        connection.createStatement().use { statement ->
            statement.execute(if (mariaDb) mariaSql else sqliteSql)
            if (!mariaDb) {
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_claim_transfer_requests_expiry " +
                        "ON $TABLE(expires_at)"
                )
            }
        }
    }

    private val sqliteSql = """
        CREATE TABLE IF NOT EXISTS $TABLE (
            claim_id TEXT NOT NULL,
            player_id TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            PRIMARY KEY (claim_id, player_id),
            FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
        )
    """.trimIndent()

    private val mariaSql = """
        CREATE TABLE IF NOT EXISTS $TABLE (
            claim_id VARCHAR(36) NOT NULL,
            player_id VARCHAR(36) NOT NULL,
            expires_at BIGINT NOT NULL,
            PRIMARY KEY (claim_id, player_id),
            INDEX idx_claim_transfer_requests_expiry (expires_at),
            CONSTRAINT fk_claim_transfer_requests_claim
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()
}
