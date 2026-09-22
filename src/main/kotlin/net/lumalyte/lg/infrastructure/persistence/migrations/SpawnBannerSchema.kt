package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object SpawnBannerSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        if (mariaDb) {
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS spawn_banners (
                        banner_id VARCHAR(36) PRIMARY KEY,
                        world_id VARCHAR(36) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        rank INT NOT NULL,
                        category VARCHAR(64) NOT NULL,
                        created_at BIGINT NOT NULL,
                        UNIQUE KEY uq_spawn_banners_location (world_id, x, y, z),
                        INDEX idx_spawn_banners_category_rank (category, rank)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.trimIndent()
                )
            }
            return
        }

        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS spawn_banners (
                    banner_id TEXT PRIMARY KEY,
                    world_id TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    rank INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    UNIQUE(world_id, x, y, z)
                )
                """.trimIndent()
            )
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_spawn_banners_category_rank " +
                    "ON spawn_banners(category, rank)"
            )
        }
    }
}
