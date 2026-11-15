package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/** Additive tables for the Prestige VI second vault (REQ-243, REQ-244). */
object SecondVaultSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val id = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val bigint = if (mariaDb) "BIGINT" else "INTEGER"
        val text = if (mariaDb) "MEDIUMTEXT" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guild_second_vaults (" +
                    "guild_id $id NOT NULL PRIMARY KEY, " +
                    "world_id $id NOT NULL, " +
                    "x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, " +
                    "placed_at $bigint NOT NULL" +
                    ")$engine",
            )
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guild_second_vault_slots (" +
                    "guild_id $id NOT NULL, " +
                    "slot INTEGER NOT NULL, " +
                    "item_data $text NOT NULL, " +
                    "PRIMARY KEY (guild_id, slot)" +
                    ")$engine",
            )
        }
    }
}
