package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import co.aikar.idb.DbRow
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestItemReward
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

class QuestRepositorySQLite(private val storage: Storage<Database>) : QuestRepository {
    init { createTables() }

    private fun createTables() {
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS weekly_quest_sets (
                week_id TEXT PRIMARY KEY, starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS weekly_quest_definitions (
                week_id TEXT NOT NULL, quest_id TEXT NOT NULL, name_key TEXT NOT NULL, description_key TEXT NOT NULL,
                action TEXT NOT NULL, target_id TEXT NOT NULL, allowed_actions TEXT NOT NULL,
                minimum_amount INTEGER NOT NULL, maximum_amount INTEGER NOT NULL,
                natural_dimensions TEXT NOT NULL, natural_biomes TEXT NOT NULL, supported_conditions TEXT NOT NULL,
                provenance_policy TEXT NOT NULL, target_count INTEGER NOT NULL, tier TEXT NOT NULL,
                condition_type TEXT, condition_value TEXT, experience_reward INTEGER NOT NULL,
                item_rewards TEXT NOT NULL, leaderboard INTEGER NOT NULL, leaderboard_payouts TEXT NOT NULL,
                PRIMARY KEY (week_id, quest_id)
            )
        """.trimIndent())
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS guild_quest_progress (
                week_id TEXT NOT NULL, quest_id TEXT NOT NULL, guild_id TEXT NOT NULL,
                current_count INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, completed_at INTEGER,
                PRIMARY KEY (week_id, quest_id, guild_id)
            )
        """.trimIndent())
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS guild_quest_weekly_bonus (
                week_id TEXT NOT NULL, guild_id TEXT NOT NULL, PRIMARY KEY (week_id, guild_id)
            )
        """.trimIndent())
        storage.connection.executeUpdate("""
            CREATE TABLE IF NOT EXISTS quest_leaderboard_payouts (
                week_id TEXT NOT NULL, quest_id TEXT NOT NULL, PRIMARY KEY (week_id, quest_id)
            )
        """.trimIndent())
    }

    override fun getActiveQuestSet(): WeeklyQuestSet? {
        val row = storage.connection.getFirstRow(
            "SELECT week_id, starts_at, ends_at FROM weekly_quest_sets WHERE active = 1 LIMIT 1"
        ) ?: return null
        val weekId = row.getString("week_id")
        val quests = storage.connection.getResults(
            "SELECT * FROM weekly_quest_definitions WHERE week_id = ? ORDER BY rowid", weekId
        ).map(::mapQuest)
        return WeeklyQuestSet(
            weekId,
            Instant.ofEpochMilli(row.longValue("starts_at")),
            Instant.ofEpochMilli(row.longValue("ends_at")),
            quests
        )
    }

    override fun saveActiveQuestSet(questSet: WeeklyQuestSet) {
        storage.connection.executeUpdate("UPDATE weekly_quest_sets SET active = 0 WHERE active = 1")
        storage.connection.executeUpdate(
            "INSERT OR REPLACE INTO weekly_quest_sets (week_id, starts_at, ends_at, active) VALUES (?, ?, ?, 1)",
            questSet.weekId, questSet.startsAt.toEpochMilli(), questSet.endsAt.toEpochMilli()
        )
        storage.connection.executeUpdate("DELETE FROM weekly_quest_definitions WHERE week_id = ?", questSet.weekId)
        questSet.quests.forEach { saveDefinition(questSet.weekId, it) }
    }

    private fun saveDefinition(weekId: String, quest: QuestDefinition) {
        val target = quest.target
        storage.connection.executeUpdate("""
            INSERT INTO weekly_quest_definitions (
                week_id, quest_id, name_key, description_key, action, target_id, allowed_actions,
                minimum_amount, maximum_amount, natural_dimensions, natural_biomes, supported_conditions,
                provenance_policy, target_count, tier, condition_type, condition_value, experience_reward,
                item_rewards, leaderboard, leaderboard_payouts
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
            weekId, quest.id, quest.nameKey, quest.descriptionKey, quest.action.name, target.id,
            target.allowedActions.joinToString(",") { it.name }, target.minimumAmount, target.maximumAmount,
            target.naturalDimensions.joinToString(","), target.naturalBiomes.joinToString(","),
            target.supportedConditions.joinToString(",") { it.name }, target.provenancePolicy.name,
            quest.targetCount, quest.tier.name, quest.condition?.type?.name, quest.condition?.value,
            quest.experienceReward, quest.itemRewards.joinToString(",") { "${it.itemId}:${it.amount}" },
            if (quest.leaderboard) 1 else 0,
            quest.leaderboardPayouts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
        )
    }

    override fun getProgress(weekId: String, questId: String, guildId: UUID): GuildQuestProgress? =
        storage.connection.getFirstRow(
            "SELECT * FROM guild_quest_progress WHERE week_id = ? AND quest_id = ? AND guild_id = ?",
            weekId, questId, guildId.toString()
        )?.let(::mapProgress)

    override fun saveProgress(value: GuildQuestProgress) {
        storage.connection.executeUpdate("""
            INSERT OR REPLACE INTO guild_quest_progress
            (week_id, quest_id, guild_id, current_count, claimed, completed_at) VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(), value.weekId, value.questId, value.guildId.toString(), value.currentCount,
            if (value.claimed) 1 else 0, value.completedAt?.toEpochMilli())
    }

    override fun getGuildProgress(weekId: String, guildId: UUID): List<GuildQuestProgress> =
        storage.connection.getResults(
            "SELECT * FROM guild_quest_progress WHERE week_id = ? AND guild_id = ?", weekId, guildId.toString()
        ).map(::mapProgress)

    override fun getQuestLeaderboard(weekId: String, questId: String, limit: Int): List<GuildQuestProgress> =
        storage.connection.getResults(
            "SELECT * FROM guild_quest_progress WHERE week_id = ? AND quest_id = ? ORDER BY current_count DESC, guild_id ASC LIMIT ?",
            weekId, questId, limit
        ).map(::mapProgress)

    override fun tryMarkClaimed(weekId: String, questId: String, guildId: UUID): Boolean =
        storage.connection.executeUpdate(
            "UPDATE guild_quest_progress SET claimed = 1 WHERE week_id = ? AND quest_id = ? AND guild_id = ? AND claimed = 0",
            weekId, questId, guildId.toString()
        ) == 1

    override fun tryMarkWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean =
        storage.connection.executeUpdate(
            "INSERT OR IGNORE INTO guild_quest_weekly_bonus (week_id, guild_id) VALUES (?, ?)", weekId, guildId.toString()
        ) == 1

    override fun isWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean =
        storage.connection.getFirstRow(
            "SELECT 1 AS found FROM guild_quest_weekly_bonus WHERE week_id = ? AND guild_id = ?", weekId, guildId.toString()
        ) != null

    override fun tryMarkLeaderboardPaid(weekId: String, questId: String): Boolean =
        storage.connection.executeUpdate(
            "INSERT OR IGNORE INTO quest_leaderboard_payouts (week_id, quest_id) VALUES (?, ?)", weekId, questId
        ) == 1

    private fun mapProgress(row: DbRow) = GuildQuestProgress(
        weekId = row.getString("week_id"),
        questId = row.getString("quest_id"),
        guildId = UUID.fromString(row.getString("guild_id")),
        currentCount = row.longValue("current_count"),
        claimed = row.getInt("claimed") == 1,
        completedAt = row.nullableLong("completed_at")?.let(Instant::ofEpochMilli)
    )

    private fun mapQuest(row: DbRow): QuestDefinition {
        val conditionType = row.nullableString("condition_type")?.let(QuestConditionType::valueOf)
        val target = QuestTarget(
            id = row.getString("target_id"),
            allowedActions = row.csv("allowed_actions").map(QuestAction::valueOf).toSet(),
            minimumAmount = row.longValue("minimum_amount"),
            maximumAmount = row.longValue("maximum_amount"),
            naturalDimensions = row.csv("natural_dimensions").toSet(),
            naturalBiomes = row.csv("natural_biomes").toSet(),
            supportedConditions = row.csv("supported_conditions").map(QuestConditionType::valueOf).toSet(),
            provenancePolicy = BlockProvenancePolicy.valueOf(row.getString("provenance_policy"))
        )
        return QuestDefinition(
            id = row.getString("quest_id"), nameKey = row.getString("name_key"),
            descriptionKey = row.getString("description_key"), action = QuestAction.valueOf(row.getString("action")),
            target = target, targetCount = row.longValue("target_count"), tier = QuestRewardTier.valueOf(row.getString("tier")),
            condition = conditionType?.let { QuestCondition(it, row.nullableString("condition_value")) },
            experienceReward = row.getInt("experience_reward"),
            itemRewards = row.csv("item_rewards").mapNotNull { entry -> entry.split(":", limit = 2).takeIf { it.size == 2 }?.let { QuestItemReward(it[0], it[1].toInt()) } },
            leaderboard = row.getInt("leaderboard") == 1,
            leaderboardPayouts = row.csv("leaderboard_payouts").mapNotNull { entry -> entry.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].toInt() to it[1].toInt() } }.toMap()
        )
    }

    private fun DbRow.csv(column: String): List<String> = getString(column).orEmpty().split(',').filter(String::isNotBlank)
    private fun DbRow.longValue(column: String): Long = when (val value = get<Any?>(column)) {
        is Number -> value.toLong()
        is String -> value.toLong()
        else -> 0L
    }
    private fun DbRow.nullableLong(column: String): Long? = when (val value = get<Any?>(column)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    private fun DbRow.nullableString(column: String): String? = get<Any?>(column)?.toString()?.takeIf(String::isNotBlank)
}
