package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object GuildGoldSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_gold_operations (
                    transaction_id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    actor_id VARCHAR(36) NOT NULL,
                    route VARCHAR(32) NOT NULL,
                    direction VARCHAR(16) NOT NULL,
                    amount BIGINT NOT NULL,
                    fee BIGINT NOT NULL,
                    old_balance BIGINT NULL,
                    new_balance BIGINT NULL,
                    status VARCHAR(32) NOT NULL,
                    rejection_reason VARCHAR(64) NULL,
                    description VARCHAR(255) NOT NULL,
                    compensation_details VARCHAR(255) NULL,
                    created_at BIGINT NOT NULL
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_gold_withdrawal_usage (
                    guild_id VARCHAR(36) NOT NULL,
                    period_start BIGINT NOT NULL,
                    amount BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, period_start)
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_gold_security (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    frozen BOOLEAN NOT NULL DEFAULT FALSE,
                    reason VARCHAR(255) NOT NULL,
                    updated_by VARCHAR(36) NOT NULL,
                    updated_at BIGINT NOT NULL
                )$engine
                """.trimIndent()
            )
        }
    }
}
