package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/** New-operation opt-in only. Never backfill eligibility from historical gold operations. */
object BankDepositXpSchema {
    const val VERSION = 47
    const val DELIVERIES = "guild_bank_xp_deliveries"
    const val SEQUENCES = "guild_bank_xp_delivery_sequences"

    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            statement.execute("""
                CREATE TABLE IF NOT EXISTS $DELIVERIES (
                    transaction_id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    actor_id VARCHAR(36) NOT NULL,
                    route VARCHAR(32) NOT NULL,
                    amount BIGINT NOT NULL,
                    fee BIGINT NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    occurred_at BIGINT NOT NULL,
                    source_pool VARCHAR(255) NOT NULL,
                    award_xp INT NOT NULL,
                    cap_xp INT NOT NULL,
                    cap_period VARCHAR(16) NOT NULL,
                    policy_enabled INT NOT NULL,
                    boost_multiplier DOUBLE NOT NULL,
                    value_per_unit BIGINT NOT NULL,
                    eligibility VARCHAR(24) NOT NULL,
                    chapter_id VARCHAR(64) NULL,
                    prestige_count INT NULL,
                    capture_denial VARCHAR(32) NULL,
                    status VARCHAR(32) NOT NULL,
                    old_balance BIGINT NULL,
                    new_balance BIGINT NULL,
                    settlement_sequence BIGINT NULL,
                    accepted_xp INT NOT NULL DEFAULT 0,
                    cache_pending INT NOT NULL DEFAULT 0,
                    last_attempt_at BIGINT NOT NULL DEFAULT 0,
                    cache_attempt_at BIGINT NOT NULL DEFAULT 0,
                    UNIQUE (guild_id, settlement_sequence)
                )$engine
            """.trimIndent())
            statement.execute("""
                CREATE TABLE IF NOT EXISTS $SEQUENCES (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    last_sequence BIGINT NOT NULL
                )$engine
            """.trimIndent())
        }
        index(connection, "idx_bank_xp_delivery_pending", "status, last_attempt_at, guild_id, settlement_sequence")
        index(connection, "idx_bank_xp_cache_pending", "cache_pending, cache_attempt_at, guild_id")
    }

    private fun index(connection: Connection, name: String, columns: String) {
        val exists = connection.metaData.getIndexInfo(connection.catalog, null, DELIVERIES, false, false).use { rows ->
            var found = false
            while (rows.next()) if (rows.getString("INDEX_NAME").equals(name, ignoreCase = true)) found = true
            found
        }
        if (!exists) connection.createStatement().use { it.execute("CREATE INDEX $name ON $DELIVERIES ($columns)") }
    }
}
