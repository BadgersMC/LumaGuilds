package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

internal object SeasonalEloSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val shortText = if (mariaDb) "VARCHAR(64)" else "TEXT"
        val integer = if (mariaDb) "BIGINT" else "INTEGER"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { s ->
            s.execute("""
                CREATE TABLE IF NOT EXISTS chapter_rated_pair_guards (
                    chapter_id $shortText NOT NULL,
                    lower_guild_id $shortText NOT NULL,
                    higher_guild_id $shortText NOT NULL,
                    last_rated_at $integer NOT NULL,
                    last_war_id $shortText NOT NULL,
                    PRIMARY KEY (chapter_id, lower_guild_id, higher_guild_id)
                )$engine
            """.trimIndent())
            s.execute("""
                CREATE TABLE IF NOT EXISTS chapter_rated_war_results (
                    war_id $shortText PRIMARY KEY,
                    chapter_id $shortText NOT NULL,
                    first_guild_id $shortText NOT NULL,
                    second_guild_id $shortText NOT NULL,
                    first_rating_before INTEGER NOT NULL,
                    second_rating_before INTEGER NOT NULL,
                    first_rating_after INTEGER NOT NULL,
                    second_rating_after INTEGER NOT NULL,
                    first_score DOUBLE NOT NULL,
                    second_score DOUBLE NOT NULL,
                    rated_at $integer NOT NULL
                )$engine
            """.trimIndent())
            s.execute("""
                CREATE TABLE IF NOT EXISTS chapter_war_rating_decisions (
                    war_id $shortText PRIMARY KEY,
                    chapter_id $shortText NOT NULL,
                    decision $shortText NOT NULL,
                    decided_at $integer NOT NULL
                )$engine
            """.trimIndent())
            s.execute("CREATE INDEX IF NOT EXISTS idx_rated_pair_last ON chapter_rated_pair_guards(chapter_id,last_rated_at)")
        }
    }
}
