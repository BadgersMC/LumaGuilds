package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/** REQ-180, REQ-182: second allied home and host access rules, for SQLite and MariaDB. */
internal object AllyHomeAccessSchema {
    const val VISITOR_RANKS = "ally_home_visitor_ranks"
    const val BLOCKED = "ally_home_blocked_players"
    const val SECOND_HOMES = "ally_home_second_homes"
    const val OUTBOUND_RANKS = "ally_home_outbound_ranks"
    const val VERSION = 51

    fun create(connection: Connection, mariaDb: Boolean) {
        val id = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val text = if (mariaDb) "TEXT" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use {
            it.executeUpdate(
                "CREATE TABLE IF NOT EXISTS $VISITOR_RANKS (" +
                    "host_guild_id $id NOT NULL, visitor_guild_id $id NOT NULL, rank_ids $text NOT NULL, " +
                    "PRIMARY KEY (host_guild_id, visitor_guild_id))$engine",
            )
            it.executeUpdate(
                "CREATE TABLE IF NOT EXISTS $BLOCKED (" +
                    "host_guild_id $id NOT NULL, player_id $id NOT NULL, " +
                    "PRIMARY KEY (host_guild_id, player_id))$engine",
            )
            it.executeUpdate(
                "CREATE TABLE IF NOT EXISTS $SECOND_HOMES (" +
                    "guild_id $id NOT NULL PRIMARY KEY, world_id $id NOT NULL, " +
                    "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL)$engine",
            )
            it.executeUpdate(
                "CREATE TABLE IF NOT EXISTS $OUTBOUND_RANKS (" +
                    "guild_id $id NOT NULL, ally_guild_id $id NOT NULL, rank_ids $text NOT NULL, " +
                    "PRIMARY KEY (guild_id, ally_guild_id))$engine",
            )
        }
    }
}
