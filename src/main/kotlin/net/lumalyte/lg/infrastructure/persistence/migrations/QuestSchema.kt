package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object QuestSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val text = if (mariaDb) "VARCHAR(255)" else "TEXT"
        val longText = if (mariaDb) "LONGTEXT" else "TEXT"
        val integer = if (mariaDb) "BIGINT" else "INTEGER"

        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS weekly_quest_sets (
                    week_id $text PRIMARY KEY,
                    starts_at $integer NOT NULL,
                    ends_at $integer NOT NULL,
                    active INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS weekly_quest_definitions (
                    week_id $text NOT NULL,
                    quest_id $text NOT NULL,
                    name_key $text NOT NULL,
                    description_key $text NOT NULL,
                    action $text NOT NULL,
                    target_id $text NOT NULL,
                    allowed_actions $longText NOT NULL,
                    minimum_amount $integer NOT NULL,
                    maximum_amount $integer NOT NULL,
                    natural_dimensions $longText NOT NULL,
                    natural_biomes $longText NOT NULL,
                    supported_conditions $longText NOT NULL,
                    provenance_policy $text NOT NULL,
                    target_rarity $text NOT NULL DEFAULT 'COMMON',
                    target_count $integer NOT NULL,
                    tier $text NOT NULL,
                    condition_type $text,
                    condition_value $text,
                    conditions $longText NOT NULL DEFAULT '',
                    quest_order INTEGER NOT NULL DEFAULT 0,
                    experience_reward INTEGER NOT NULL,
                    item_rewards $longText NOT NULL,
                    leaderboard INTEGER NOT NULL,
                    leaderboard_payouts $longText NOT NULL,
                    PRIMARY KEY (week_id, quest_id)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_quest_progress (
                    week_id $text NOT NULL,
                    quest_id $text NOT NULL,
                    guild_id $text NOT NULL,
                    current_count $integer NOT NULL DEFAULT 0,
                    claimed INTEGER NOT NULL DEFAULT 0,
                    completed_at $integer,
                    claim_actor_id $text,
                    reward_delivered INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (week_id, quest_id, guild_id)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS guild_quest_weekly_bonus (
                    week_id $text NOT NULL,
                    guild_id $text NOT NULL,
                    PRIMARY KEY (week_id, guild_id)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS quest_leaderboard_payouts (
                    week_id $text NOT NULL,
                    quest_id $text NOT NULL,
                    guild_id $text NOT NULL,
                    PRIMARY KEY (week_id, quest_id, guild_id)
                )
                """.trimIndent()
            )
        }

        ensureColumn(connection, "weekly_quest_definitions", "target_rarity", "$text NOT NULL DEFAULT 'COMMON'")
        ensureColumn(connection, "weekly_quest_definitions", "conditions", "$longText NOT NULL DEFAULT ''")
        ensureColumn(connection, "weekly_quest_definitions", "quest_order", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(connection, "guild_quest_progress", "claim_actor_id", text)
        val addedRewardDelivered = ensureColumn(
            connection,
            "guild_quest_progress",
            "reward_delivered",
            "INTEGER NOT NULL DEFAULT 0",
        )
        if (addedRewardDelivered) {
            // Existing claimed quests predate durable reward reconciliation. Treat them
            // as already delivered so an upgrade cannot repay historical rewards.
            connection.createStatement().use {
                it.executeUpdate("UPDATE guild_quest_progress SET reward_delivered = claimed")
            }
        }
    }

    private fun ensureColumn(
        connection: Connection,
        table: String,
        column: String,
        definition: String,
    ): Boolean {
        if (hasColumn(connection, table, column)) return false
        connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
        return true
    }

    private fun hasColumn(connection: Connection, table: String, column: String): Boolean {
        connection.metaData.getColumns(null, null, table, null).use { rows ->
            while (rows.next()) {
                if (rows.getString("COLUMN_NAME").equals(column, ignoreCase = true)) return true
            }
        }
        return false
    }
}
