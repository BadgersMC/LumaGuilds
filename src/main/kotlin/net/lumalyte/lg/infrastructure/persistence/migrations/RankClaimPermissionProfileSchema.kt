package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object RankClaimPermissionProfileSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val rankIdType = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val profileNameType = if (mariaDb) "VARCHAR(255)" else "TEXT"
        val engine = if (mariaDb) {
            " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        } else {
            ""
        }

        connection.createStatement().use { statement ->
            statement.execute(
                """CREATE TABLE IF NOT EXISTS rank_claim_permission_profiles (
                    rank_id $rankIdType PRIMARY KEY,
                    profile_name $profileNameType NOT NULL,
                    FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE
                )$engine""".trimIndent()
            )
        }
    }
}
