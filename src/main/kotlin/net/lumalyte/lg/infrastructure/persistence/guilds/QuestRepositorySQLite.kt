package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import co.aikar.idb.DbRow
import co.aikar.idb.DbStatement
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestItemReward
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.Base64
import java.util.UUID

class QuestRepositorySQLite(private val storage: Storage<Database>) : QuestRepository {
    private val mariaDb = storage.javaClass.simpleName.contains("MariaDB")

    private val questSetUpsertSql = if (mariaDb) {
        """
        INSERT INTO weekly_quest_sets (week_id, starts_at, ends_at, active)
        VALUES (?, ?, ?, 1)
        ON DUPLICATE KEY UPDATE starts_at = VALUES(starts_at), ends_at = VALUES(ends_at), active = 1
        """.trimIndent()
    } else {
        "INSERT OR REPLACE INTO weekly_quest_sets (week_id, starts_at, ends_at, active) VALUES (?, ?, ?, 1)"
    }

    private val progressUpsertSql = if (mariaDb) {
        """
        INSERT INTO guild_quest_progress
        (week_id, quest_id, guild_id, current_count, claimed, completed_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            current_count = GREATEST(current_count, VALUES(current_count)),
            claimed = GREATEST(claimed, VALUES(claimed)),
            completed_at = COALESCE(completed_at, VALUES(completed_at))
        """.trimIndent()
    } else {
        """
        INSERT INTO guild_quest_progress
        (week_id, quest_id, guild_id, current_count, claimed, completed_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(week_id, quest_id, guild_id) DO UPDATE SET
            current_count = MAX(current_count, excluded.current_count),
            claimed = MAX(claimed, excluded.claimed),
            completed_at = COALESCE(completed_at, excluded.completed_at)
        """.trimIndent()
    }

    private val bonusInsertSql = if (mariaDb) {
        "INSERT IGNORE INTO guild_quest_weekly_bonus (week_id, guild_id) VALUES (?, ?)"
    } else {
        "INSERT OR IGNORE INTO guild_quest_weekly_bonus (week_id, guild_id) VALUES (?, ?)"
    }

    private val payoutInsertSql = if (mariaDb) {
        "INSERT IGNORE INTO quest_leaderboard_payouts (week_id, quest_id, guild_id) VALUES (?, ?, ?)"
    } else {
        "INSERT OR IGNORE INTO quest_leaderboard_payouts (week_id, quest_id, guild_id) VALUES (?, ?, ?)"
    }
    override fun getActiveQuestSet(): WeeklyQuestSet? =
        storage.connection.getFirstRow(
            "SELECT week_id, starts_at, ends_at FROM weekly_quest_sets WHERE active = 1 LIMIT 1"
        )?.let(::mapQuestSet)

    override fun getRecentQuestSets(limit: Int): List<WeeklyQuestSet> {
        if (limit <= 0) return emptyList()
        return storage.connection.getResults(
            "SELECT week_id, starts_at, ends_at FROM weekly_quest_sets ORDER BY starts_at DESC LIMIT ?",
            limit
        ).map(::mapQuestSet)
    }

    override fun saveActiveQuestSet(questSet: WeeklyQuestSet) {
        val committed = storage.connection.createTransaction { statement ->
            statement.executeUpdateQuery("UPDATE weekly_quest_sets SET active = 0 WHERE active = 1")
            statement.executeUpdateQuery(
                questSetUpsertSql,
                questSet.weekId,
                questSet.startsAt.toEpochMilli(),
                questSet.endsAt.toEpochMilli()
            )
            statement.executeUpdateQuery(
                "DELETE FROM weekly_quest_definitions WHERE week_id = ?",
                questSet.weekId
            )
            questSet.quests.forEachIndexed { index, quest ->
                saveDefinition(statement, questSet.weekId, index, quest)
            }
            true
        }
        check(committed) { "Weekly quest-set transaction did not commit" }
    }

    override fun deactivateActiveQuestSet() {
        storage.connection.executeUpdate("UPDATE weekly_quest_sets SET active = 0 WHERE active = 1")
    }

    private fun saveDefinition(
        statement: DbStatement,
        weekId: String,
        order: Int,
        quest: QuestDefinition
    ) {
        val target = quest.target
        val firstCondition = quest.conditions.firstOrNull()
        statement.executeUpdateQuery(
            """
            INSERT INTO weekly_quest_definitions (
                week_id, quest_id, name_key, description_key, action, target_id, allowed_actions,
                minimum_amount, maximum_amount, natural_dimensions, natural_biomes, supported_conditions,
                provenance_policy, target_rarity, target_count, tier, condition_type, condition_value,
                conditions, quest_order, experience_reward, item_rewards, leaderboard, leaderboard_payouts
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            weekId,
            quest.id,
            quest.nameKey,
            quest.descriptionKey,
            quest.action.name,
            target.id,
            target.allowedActions.joinToString(",") { it.name },
            target.minimumAmount,
            target.maximumAmount,
            target.naturalDimensions.joinToString(","),
            target.naturalBiomes.joinToString(","),
            target.supportedConditions.joinToString(",") { it.name },
            target.provenancePolicy.name,
            target.rarity.name,
            quest.targetCount,
            quest.tier.name,
            firstCondition?.type?.name,
            firstCondition?.value,
            encodeConditions(quest.conditions),
            order,
            quest.experienceReward,
            quest.itemRewards.joinToString(",") { "${it.amount}:${encode(it.itemId)}" },
            if (quest.leaderboard) 1 else 0,
            quest.leaderboardPayouts.entries.sortedBy { it.key }
                .joinToString(",") { "${it.key}:${it.value}" }
        )
    }

    override fun getProgress(
        weekId: String,
        questId: String,
        guildId: UUID
    ): GuildQuestProgress? =
        storage.connection.getFirstRow(
            "SELECT * FROM guild_quest_progress WHERE week_id = ? AND quest_id = ? AND guild_id = ?",
            weekId,
            questId,
            guildId.toString()
        )?.let(::mapProgress)

    override fun saveProgress(value: GuildQuestProgress) {
        storage.connection.executeUpdate(
            progressUpsertSql,
            value.weekId,
            value.questId,
            value.guildId.toString(),
            value.currentCount,
            if (value.claimed) 1 else 0,
            value.completedAt?.toEpochMilli()
        )
    }

    override fun getGuildProgress(weekId: String, guildId: UUID): List<GuildQuestProgress> =
        storage.connection.getResults(
            "SELECT * FROM guild_quest_progress WHERE week_id = ? AND guild_id = ?",
            weekId,
            guildId.toString()
        ).map(::mapProgress)

    override fun getQuestLeaderboard(
        weekId: String,
        questId: String,
        limit: Int
    ): List<GuildQuestProgress> =
        storage.connection.getResults(
            """
            SELECT * FROM guild_quest_progress
            WHERE week_id = ? AND quest_id = ?
            ORDER BY current_count DESC, guild_id ASC
            LIMIT ?
            """.trimIndent(),
            weekId,
            questId,
            limit
        ).map(::mapProgress)

    override fun tryMarkClaimed(weekId: String, questId: String, guildId: UUID): Boolean =
        storage.connection.executeUpdate(
            """
            UPDATE guild_quest_progress
            SET claimed = 1
            WHERE week_id = ? AND quest_id = ? AND guild_id = ? AND claimed = 0
            """.trimIndent(),
            weekId,
            questId,
            guildId.toString()
        ) == 1

    override fun tryMarkWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean =
        storage.connection.executeUpdate(
            bonusInsertSql,
            weekId,
            guildId.toString()
        ) == 1

    override fun isWeeklyBonusAwarded(weekId: String, guildId: UUID): Boolean =
        storage.connection.getFirstRow(
            "SELECT 1 AS found FROM guild_quest_weekly_bonus WHERE week_id = ? AND guild_id = ?",
            weekId,
            guildId.toString()
        ) != null

    override fun isLeaderboardRecipientPaid(
        weekId: String,
        questId: String,
        guildId: UUID
    ): Boolean =
        storage.connection.getFirstRow(
            """
            SELECT 1 AS found FROM quest_leaderboard_payouts
            WHERE week_id = ? AND quest_id = ? AND guild_id = ?
            """.trimIndent(),
            weekId,
            questId,
            guildId.toString()
        ) != null

    override fun markLeaderboardRecipientPaid(weekId: String, questId: String, guildId: UUID) {
        storage.connection.executeUpdate(
            payoutInsertSql,
            weekId,
            questId,
            guildId.toString()
        )
    }

    override fun deleteWeekProgress(weekId: String) {
        storage.connection.executeUpdate(
            "DELETE FROM guild_quest_progress WHERE week_id = ?",
            weekId
        )
        storage.connection.executeUpdate(
            "DELETE FROM guild_quest_weekly_bonus WHERE week_id = ?",
            weekId
        )
    }

    private fun mapQuestSet(row: DbRow): WeeklyQuestSet {
        val weekId = row.getString("week_id")
        val quests = storage.connection.getResults(
            """
            SELECT * FROM weekly_quest_definitions
            WHERE week_id = ?
            ORDER BY quest_order ASC, quest_id ASC
            """.trimIndent(),
            weekId
        ).map(::mapQuest)

        return WeeklyQuestSet(
            weekId = weekId,
            startsAt = Instant.ofEpochMilli(row.longValue("starts_at")),
            endsAt = Instant.ofEpochMilli(row.longValue("ends_at")),
            quests = quests
        )
    }

    private fun mapProgress(row: DbRow) = GuildQuestProgress(
        weekId = row.getString("week_id"),
        questId = row.getString("quest_id"),
        guildId = UUID.fromString(row.getString("guild_id")),
        currentCount = row.longValue("current_count"),
        claimed = row.getInt("claimed") == 1,
        completedAt = row.nullableLong("completed_at")?.let(Instant::ofEpochMilli)
    )

    private fun mapQuest(row: DbRow): QuestDefinition {
        val action = QuestAction.valueOf(row.getString("action"))
        val rawTargetId = row.getString("target_id")
        val targetId = normalizeTargetId(action, rawTargetId)
        val minimum = row.longValue("minimum_amount").coerceAtLeast(1)
        val maximum = row.longValue("maximum_amount").coerceAtLeast(minimum)
        val allowedActions = row.csv("allowed_actions")
            .mapNotNull { runCatching { QuestAction.valueOf(it) }.getOrNull() }
            .toSet()
            .ifEmpty { setOf(action) }
        val conditions = decodeConditions(row)

        val target = QuestTarget(
            id = targetId,
            allowedActions = allowedActions,
            minimumAmount = minimum,
            maximumAmount = maximum,
            naturalDimensions = row.csv("natural_dimensions").toSet(),
            naturalBiomes = row.csv("natural_biomes").toSet(),
            supportedConditions = row.csv("supported_conditions")
                .mapNotNull { runCatching { QuestConditionType.valueOf(it) }.getOrNull() }
                .toSet() + conditions.map { it.type },
            provenancePolicy = runCatching {
                BlockProvenancePolicy.valueOf(row.getString("provenance_policy"))
            }.getOrDefault(BlockProvenancePolicy.ANY),
            rarity = row.nullableString("target_rarity")?.let {
                runCatching { QuestTargetRarity.valueOf(it) }.getOrNull()
            } ?: QuestTargetRarity.COMMON
        )

        return QuestDefinition(
            id = row.getString("quest_id"),
            nameKey = row.getString("name_key"),
            descriptionKey = row.getString("description_key"),
            action = action,
            target = target,
            targetCount = row.longValue("target_count").coerceAtLeast(1),
            tier = QuestRewardTier.valueOf(row.getString("tier")),
            conditions = conditions,
            experienceReward = row.getInt("experience_reward"),
            itemRewards = row.csv("item_rewards").mapNotNull { entry ->
                entry.split(":", limit = 2).takeIf { it.size == 2 }?.let {
                    QuestItemReward(decode(it[1]), it[0].toInt())
                }
            },
            leaderboard = row.getInt("leaderboard") == 1,
            leaderboardPayouts = row.csv("leaderboard_payouts").mapNotNull { entry ->
                entry.split(":", limit = 2).takeIf { it.size == 2 }?.let {
                    it[0].toInt() to it[1].toInt()
                }
            }.toMap()
        )
    }

    private fun decodeConditions(row: DbRow): List<QuestCondition> {
        val encoded = row.nullableString("conditions")
        if (!encoded.isNullOrBlank()) {
            return encoded.split(',').mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val type = runCatching { QuestConditionType.valueOf(parts[0]) }.getOrNull()
                    ?: return@mapNotNull null
                val value = decode(parts[1]).takeIf(String::isNotEmpty)
                QuestCondition(type, value)
            }
        }

        val legacyType = row.nullableString("condition_type")
            ?.let { runCatching { QuestConditionType.valueOf(it) }.getOrNull() }
            ?: return emptyList()
        return listOf(QuestCondition(legacyType, row.nullableString("condition_value")))
    }

    private fun encodeConditions(conditions: List<QuestCondition>): String =
        conditions.joinToString(",") { condition ->
            "${condition.type.name}:${encode(condition.value.orEmpty())}"
        }

    private fun normalizeTargetId(action: QuestAction, raw: String): String {
        if (':' in raw && '/' in raw.substringAfter(':')) return raw.lowercase()
        val value = raw.lowercase()
        return when (action) {
            QuestAction.KILL_PLAYERS -> "minecraft:player/player"
            QuestAction.KILL_MOBS -> "minecraft:entity/$value"
            QuestAction.HARVEST_CROPS,
            QuestAction.MINE_BLOCKS,
            QuestAction.PLACE_BLOCKS -> "minecraft:block/$value"
            QuestAction.CRAFT_ITEMS,
            QuestAction.SMELT_ITEMS,
            QuestAction.FISH,
            QuestAction.ENCHANT_ITEMS -> "minecraft:item/$value"
            QuestAction.DEPOSIT_BANK -> "lumaguilds:bank/coins"
            QuestAction.WIN_WARS -> "lumaguilds:war/win"
        }
    }

    private fun DbRow.csv(column: String): List<String> =
        getString(column).orEmpty().split(',').filter(String::isNotBlank)

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

    private fun DbRow.nullableString(column: String): String? =
        get<Any?>(column)?.toString()?.takeIf(String::isNotBlank)

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value))
}
