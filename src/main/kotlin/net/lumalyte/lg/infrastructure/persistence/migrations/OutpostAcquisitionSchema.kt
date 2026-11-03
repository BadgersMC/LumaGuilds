package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/**
 * Chapter 2 Outpost purchase proofs are separate from historical reward ownership.
 * Existing permanent Outpost receipts remain immutable evidence, not active grants.
 */
internal object OutpostAcquisitionSchema {
    const val VERSION = 50

    fun create(connection: Connection, mariaDb: Boolean) {
        val engine = if (mariaDb) " ENGINE=InnoDB" else ""
        connection.createStatement().use { statement ->
            statement.execute("""CREATE TABLE IF NOT EXISTS guild_reward_accounts (
                guild_id VARCHAR(36) PRIMARY KEY,
                version BIGINT NOT NULL,
                initial_home_capacity INTEGER NOT NULL,
                prestige_count INTEGER NOT NULL,
                base_home_slots_earned INTEGER NOT NULL DEFAULT 1
            )$engine""")
        }
        // In-place, additive migration. Do not derive this from initial_home_capacity:
        // it can be a post-exploit saved-home count, not the old earned slot limit.
        val hasBase = connection.metaData.getColumns(connection.catalog, null, "guild_reward_accounts", "%").use { columns ->
            generateSequence { if (columns.next()) columns.getString("COLUMN_NAME") else null }
                .any { it.equals("base_home_slots_earned", ignoreCase = true) }
        }
        if (!hasBase) connection.createStatement().use {
            it.execute("ALTER TABLE guild_reward_accounts ADD COLUMN base_home_slots_earned INTEGER NOT NULL DEFAULT 1")
        }
        connection.createStatement().use { statement ->
            statement.execute("""CREATE TABLE IF NOT EXISTS guild_outpost_acquisitions (
                transaction_id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                prestige_rank INTEGER NOT NULL,
                reward_id VARCHAR(64) NOT NULL,
                paid_amount BIGINT NOT NULL,
                acquired_at BIGINT NOT NULL,
                assigned_home_name VARCHAR(128) NULL,
                UNIQUE (guild_id, prestige_rank),
                UNIQUE (guild_id, reward_id)
            )$engine""")
        }
    }
}
