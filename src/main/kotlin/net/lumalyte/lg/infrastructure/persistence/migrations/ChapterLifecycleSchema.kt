package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

internal object ChapterLifecycleSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val integer = if (mariaDb) "BIGINT" else "INTEGER"
        val text = if (mariaDb) "VARCHAR(255)" else "TEXT"
        val shortText = if (mariaDb) "VARCHAR(64)" else "TEXT"
        val bool = if (mariaDb) "TINYINT(1)" else "INTEGER"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""

        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_reward_accounts (
                    guild_id $shortText PRIMARY KEY,
                    version $integer NOT NULL DEFAULT 0,
                    initial_home_capacity INTEGER NOT NULL,
                    prestige_count INTEGER NOT NULL DEFAULT 0
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_reward_ownership (
                    guild_id $shortText NOT NULL,
                    reward_id $shortText NOT NULL,
                    permanent $bool NOT NULL,
                    PRIMARY KEY (guild_id, reward_id)
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS chapter_lifecycle (
                    chapter_id $shortText PRIMARY KEY,
                    chapter_name $text NOT NULL,
                    phase $shortText NOT NULL,
                    starts_at $integer,
                    ends_at $integer,
                    backup_id $shortText,
                    archived_at $integer,
                    last_error TEXT,
                    transition_token $shortText,
                    updated_at $integer NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS chapter_standings_archive (
                    chapter_id $shortText NOT NULL,
                    guild_id $shortText NOT NULL,
                    placement INTEGER NOT NULL,
                    run_level INTEGER NOT NULL,
                    run_experience $integer NOT NULL,
                    seasonal_elo INTEGER NOT NULL DEFAULT 1000,
                    canonical_gold $integer NOT NULL DEFAULT 0,
                    archived_at $integer NOT NULL,
                    PRIMARY KEY (chapter_id, guild_id)
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS chapter_backup_evidence (
                    backup_id $shortText PRIMARY KEY,
                    chapter_id $shortText NOT NULL,
                    storage_ref TEXT NOT NULL,
                    sha256 $text NOT NULL,
                    size_bytes $integer NOT NULL,
                    created_at $integer NOT NULL,
                    verified_at $integer,
                    verification_status $shortText NOT NULL,
                    restore_verified_at $integer,
                    notes TEXT
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS chapter_migrations (
                    migration_id $shortText NOT NULL,
                    source_chapter_id $shortText NOT NULL,
                    target_chapter_id $shortText NOT NULL,
                    migration_kind $shortText NOT NULL,
                    status $shortText NOT NULL,
                    completed_at $integer NOT NULL,
                    PRIMARY KEY (migration_id, source_chapter_id, target_chapter_id)
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS chapter_migration_receipts (
                    migration_id $shortText NOT NULL,
                    source_chapter_id $shortText NOT NULL,
                    target_chapter_id $shortText NOT NULL,
                    guild_id $shortText NOT NULL,
                    migration_kind $shortText NOT NULL,
                    dry_run $bool NOT NULL DEFAULT 0,
                    status $shortText NOT NULL,
                    before_level INTEGER,
                    before_experience $integer,
                    home_count INTEGER,
                    initial_home_capacity INTEGER,
                    applied_at $integer,
                    error TEXT,
                    PRIMARY KEY (migration_id, source_chapter_id, target_chapter_id, guild_id)
                )$engine
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS chapter_seasonal_ratings (
                    chapter_id $shortText NOT NULL,
                    guild_id $shortText NOT NULL,
                    elo INTEGER NOT NULL DEFAULT 1000,
                    updated_at $integer NOT NULL,
                    PRIMARY KEY (chapter_id, guild_id)
                )$engine
                """.trimIndent()
            )
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chapter_lifecycle_phase ON chapter_lifecycle(phase)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chapter_archive_placement ON chapter_standings_archive(chapter_id, placement)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chapter_backup_status ON chapter_backup_evidence(chapter_id, verification_status)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chapter_migration_status ON chapter_migration_receipts(migration_id, status)")
        }
    }
}
