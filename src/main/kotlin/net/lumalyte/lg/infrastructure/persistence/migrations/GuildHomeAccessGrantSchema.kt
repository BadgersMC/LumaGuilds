package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/**
 * Durable home access is independent of guild_homes: the guild repository
 * DELETEs/REINSERTs guild_homes rows on every guild save.
 */
object GuildHomeAccessGrantSchema {
    const val VERSION = 51
    const val POLICY = "guild_home_access_policies"
    const val GRANTS = "guild_home_access_grants"

    fun create(connection: Connection, mariaDb: Boolean) {
        val id = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val home = if (mariaDb) "VARCHAR(64)" else "TEXT"
        val source = if (mariaDb) "VARCHAR(96)" else "TEXT"
        val provenance = if (mariaDb) "VARCHAR(512)" else "TEXT"
        val state = if (mariaDb) "VARCHAR(32)" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use {
            it.executeUpdate("""
                CREATE TABLE IF NOT EXISTS $POLICY (
                    guild_id $id NOT NULL PRIMARY KEY,
                    state $state NOT NULL,
                    provenance_ref $provenance,
                    reviewer_id $id,
                    reviewed_at BIGINT,
                    version BIGINT NOT NULL DEFAULT 0,
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )$engine
            """.trimIndent())
            it.executeUpdate("""
                CREATE TABLE IF NOT EXISTS $GRANTS (
                    guild_id $id NOT NULL,
                    home_name $home NOT NULL,
                    state $state NOT NULL,
                    source_key $source,
                    provenance_ref $provenance,
                    reviewer_id $id,
                    assigned_at BIGINT NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, home_name),
                    UNIQUE (guild_id, source_key),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )$engine
            """.trimIndent())
        }
    }
}
