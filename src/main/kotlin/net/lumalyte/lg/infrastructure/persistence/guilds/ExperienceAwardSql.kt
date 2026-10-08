package net.lumalyte.lg.infrastructure.persistence.guilds

internal fun usageSeedSql(mariaDb: Boolean): String {
    return if (mariaDb) {
        MARIADB_USAGE_SEED_SQL
    } else {
        SQLITE_USAGE_SEED_SQL
    }
}

internal fun progressionUpsertSql(mariaDb: Boolean): String {
    return if (mariaDb) {
        MARIADB_PROGRESSION_UPSERT_SQL
    } else {
        SQLITE_PROGRESSION_UPSERT_SQL
    }
}

private val MARIADB_USAGE_SEED_SQL = """
    INSERT INTO guild_experience_source_usage
        (guild_id, source_pool, period_start, period_end, awarded_xp)
    VALUES (?, ?, ?, ?, 0)
    ON DUPLICATE KEY UPDATE period_end = VALUES(period_end)
""".trimIndent()

private val SQLITE_USAGE_SEED_SQL = """
    INSERT INTO guild_experience_source_usage
        (guild_id, source_pool, period_start, period_end, awarded_xp)
    VALUES (?, ?, ?, ?, 0)
    ON CONFLICT(guild_id, source_pool, period_start)
    DO UPDATE SET period_end = excluded.period_end
""".trimIndent()

private val MARIADB_PROGRESSION_UPSERT_SQL = """
    INSERT INTO guild_progression
        (guild_id, total_experience, current_level, experience_this_level,
         experience_for_next_level, last_level_up, total_level_ups,
         unlocked_perks, created_at, last_updated)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE
        total_experience = VALUES(total_experience),
        current_level = VALUES(current_level),
        experience_this_level = VALUES(experience_this_level),
        experience_for_next_level = VALUES(experience_for_next_level),
        last_level_up = COALESCE(VALUES(last_level_up), last_level_up),
        total_level_ups = VALUES(total_level_ups),
        last_updated = VALUES(last_updated)
""".trimIndent()

private val SQLITE_PROGRESSION_UPSERT_SQL = """
    INSERT INTO guild_progression
        (guild_id, total_experience, current_level, experience_this_level,
         experience_for_next_level, last_level_up, total_level_ups,
         unlocked_perks, created_at, last_updated)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(guild_id) DO UPDATE SET
        total_experience = excluded.total_experience,
        current_level = excluded.current_level,
        experience_this_level = excluded.experience_this_level,
        experience_for_next_level = excluded.experience_for_next_level,
        last_level_up = COALESCE(excluded.last_level_up, guild_progression.last_level_up),
        total_level_ups = excluded.total_level_ups,
        last_updated = excluded.last_updated
""".trimIndent()
