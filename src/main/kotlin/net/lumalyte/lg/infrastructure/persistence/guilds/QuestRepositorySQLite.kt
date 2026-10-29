package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import co.aikar.idb.DbRow
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.persistence.BankQuestContribution
import net.lumalyte.lg.application.persistence.PendingBankContribution
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
import java.sql.Connection
import java.time.Instant
import java.util.Base64
import java.util.UUID

class QuestRepositorySQLite(private val storage: Storage<Database>) : QuestRepository {
    private val mariaDb = storage.javaClass.simpleName.contains("MariaDB")

    override fun currentBankQuestRevision(): Long = storage.connection.connection.use { connection ->
        checkNotNull(connection.selectOne("SELECT revision FROM guild_bank_quest_revision WHERE id = 1") { it.getLong(1) })
    }

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
        (week_id, quest_id, guild_id, current_count, claimed, completed_at, claim_actor_id, reward_delivered)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            current_count = GREATEST(current_count, VALUES(current_count)),
            claimed = GREATEST(claimed, VALUES(claimed)),
            completed_at = COALESCE(completed_at, VALUES(completed_at)),
            claim_actor_id = COALESCE(claim_actor_id, VALUES(claim_actor_id)),
            reward_delivered = GREATEST(reward_delivered, VALUES(reward_delivered))
        """.trimIndent()
    } else {
        """
        INSERT INTO guild_quest_progress
        (week_id, quest_id, guild_id, current_count, claimed, completed_at, claim_actor_id, reward_delivered)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(week_id, quest_id, guild_id) DO UPDATE SET
            current_count = MAX(current_count, excluded.current_count),
            claimed = MAX(claimed, excluded.claimed),
            completed_at = COALESCE(completed_at, excluded.completed_at),
            claim_actor_id = COALESCE(claim_actor_id, excluded.claim_actor_id),
            reward_delivered = MAX(reward_delivered, excluded.reward_delivered)
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
    override fun pendingBankContributions(limit: Int): List<PendingBankContribution> {
        require(limit in 1..128)
        // Quest tables can inherit the server default collation, unlike contribution tables.
        // Compare identifiers byte-for-byte across both schemas on MariaDB.
        fun same(left: String, right: String) = if (mariaDb) "BINARY $left = BINARY $right" else "$left = $right"
        val sql = """
            SELECT DISTINCT i.transaction_id, r.guild_id, o.actor_id, COALESCE(attempts.attempts, 0) AS attempt_count
            FROM guild_bank_quest_intents i
            LEFT JOIN guild_bank_quest_attempts attempts ON attempts.transaction_id = i.transaction_id
            JOIN guild_bank_contribution_receipts r ON ${same("r.transaction_id", "i.transaction_id")}
            JOIN guild_gold_operations o ON ${same("o.transaction_id", "i.transaction_id")} AND ${same("o.guild_id", "r.guild_id")}
            JOIN weekly_quest_sets w ON ${same("w.week_id", "i.week_id")} AND w.active = 1
            WHERE o.status = 'APPLIED' AND NOT EXISTS (
                SELECT 1 FROM guild_bank_quest_recovery attempted WHERE attempted.transaction_id = i.transaction_id
            ) AND NOT EXISTS (
                SELECT 1 FROM guild_bank_contribution_quests done
                WHERE done.transaction_id = i.transaction_id AND done.quest_id = i.quest_id
            )
            ORDER BY attempt_count, i.transaction_id LIMIT ?
        """.trimIndent()
        return storage.connection.connection.use { c ->
            c.prepareStatement(sql).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(PendingBankContribution(
                            UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                            UUID.fromString(rows.getString(3)),
                        ))
                    }
                }
            }
        }
    }

    override fun noteBankContributionAttempt(transactionId: UUID) {
        val sql = if (mariaDb) {
            "INSERT INTO guild_bank_quest_attempts (transaction_id, attempts) VALUES (?, 1) " +
                "ON DUPLICATE KEY UPDATE attempts = CASE WHEN attempts < 9223372036854775807 THEN attempts + 1 ELSE attempts END"
        } else {
            "INSERT INTO guild_bank_quest_attempts (transaction_id, attempts) VALUES (?, 1) " +
                "ON CONFLICT(transaction_id) DO UPDATE SET attempts = CASE WHEN attempts < 9223372036854775807 THEN attempts + 1 ELSE attempts END"
        }
        storage.connection.connection.use { c ->
            c.prepareStatement(sql).use { statement ->
                statement.setString(1, transactionId.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun finishBankContributionRecovery(transactionId: UUID) {
        val insert = if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE"
        storage.connection.connection.use { c ->
            c.prepareStatement("$insert INTO guild_bank_quest_recovery (transaction_id) VALUES (?)").use { statement ->
                statement.setString(1, transactionId.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun applyBankContribution(input: BankQuestContribution): GuildQuestProgress? {
        return storage.connection.connection.use { c ->
            c.committingTransaction { consumeBankContribution(c, input) }
        }
    }

    private fun consumeBankContribution(c: java.sql.Connection, input: BankQuestContribution): GuildQuestProgress? {
        val amount = availableBankAmount(c, input)
        return if (amount != null && amount > 0 && claimBankContribution(c, input)) {
            updateBankProgress(c, input, amount)
        } else {
            null
        }
    }

    private fun availableBankAmount(c: java.sql.Connection, input: BankQuestContribution): Long? {
        val run = BankDepositXpLedger(mariaDb).fence(c, input.guildId)
        return if (run.guildExists && run.chapter?.allows(Instant.now().toEpochMilli()) != false &&
            matchesCapturedQuest(c, input)) {
            verifiedBankAmount(c, input, run)
        } else {
            null
        }
    }

    private fun matchesCapturedQuest(c: Connection, input: BankQuestContribution): Boolean {
        val captured = c.selectOne(
            "SELECT fingerprint FROM guild_bank_quest_intents WHERE transaction_id = ? AND week_id = ? AND quest_id = ?",
            input.transactionId.toString(), input.weekId, input.questId,
        ) { it.getString(1) } ?: return false
        val occurredAt = c.selectOne(
            "SELECT occurred_at FROM guild_bank_xp_deliveries WHERE transaction_id = ?",
            input.transactionId.toString(),
        ) { it.getLong(1) } ?: return false
        return QuestDefinitionFingerprint.activeBankDefinitions(c, occurredAt, lock = mariaDb)
            .any { it.weekId == input.weekId && it.questId == input.questId && it.fingerprint == captured }
    }

    private fun updateBankProgress(
        c: java.sql.Connection,
        input: BankQuestContribution,
        amount: Long,
    ): GuildQuestProgress {
        val updated = bankProgress(c, input).withIncrementedCount(amount, input.targetCount)
        c.updateStatement(
            "UPDATE guild_quest_progress SET current_count = ?, completed_at = ? " +
                "WHERE week_id = ? AND quest_id = ? AND guild_id = ?",
            updated.currentCount,
            updated.completedAt?.toEpochMilli(),
            input.weekId,
            input.questId,
            input.guildId.toString(),
        )
        return updated
    }

    private fun verifiedBankAmount(
        c: java.sql.Connection,
        input: BankQuestContribution,
        run: BankDepositXpRun,
    ): Long? {
        // Retained provenance supplies the amount, identity, original time and run.
        return c.selectOne(
            VERIFIED_BANK_CONTRIBUTION_SQL,
            input.weekId,
            input.questId,
            input.transactionId.toString(),
            input.guildId.toString(),
            input.actorId.toString(),
            run.prestige,
            run.chapter?.id,
            run.chapter?.id,
            input.targetCount,
        ) { it.getLong(1) }
    }

    private fun claimBankContribution(c: java.sql.Connection, input: BankQuestContribution): Boolean {
        val insert = if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE"
        return c.updateStatement(
            "$insert INTO guild_bank_contribution_quests (transaction_id, quest_id, week_id) VALUES (?, ?, ?)",
            input.transactionId.toString(),
            input.questId,
            input.weekId,
        ) == 1
    }

    private fun bankProgress(c: java.sql.Connection, input: BankQuestContribution): GuildQuestProgress {
        val insert = if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE"
        c.updateStatement(
            "$insert INTO guild_quest_progress " +
                "(week_id, quest_id, guild_id, current_count, claimed, reward_delivered) VALUES (?, ?, ?, 0, 0, 0)",
            input.weekId,
            input.questId,
            input.guildId.toString(),
        )
        val lock = if (mariaDb) " FOR UPDATE" else ""
        return checkNotNull(
            c.selectOne(
                "SELECT * FROM guild_quest_progress WHERE week_id = ? AND quest_id = ? AND guild_id = ?$lock",
                input.weekId,
                input.questId,
                input.guildId.toString(),
            ) { row -> mapBankProgress(row, input) },
        )
    }

    private fun mapBankProgress(row: java.sql.ResultSet, input: BankQuestContribution): GuildQuestProgress {
        return GuildQuestProgress(
            input.weekId,
            input.questId,
            input.guildId,
            row.getLong("current_count"),
            row.getInt("claimed") != 0,
            row.getObject("completed_at")?.let { Instant.ofEpochMilli(row.getLong("completed_at")) },
            row.getString("claim_actor_id")?.let(UUID::fromString),
            row.getInt("reward_delivered") != 0,
        )
    }

    override fun getActiveQuestSet(): WeeklyQuestSet? =
        storage.connection.getFirstRow(
            "SELECT week_id, starts_at, ends_at FROM weekly_quest_sets WHERE active = 1 LIMIT 1"
        )?.let(::mapQuestSet)

    override fun getQuestSet(weekId: String): WeeklyQuestSet? =
        storage.connection.getFirstRow(
            "SELECT week_id, starts_at, ends_at FROM weekly_quest_sets WHERE week_id = ?",
            weekId,
        )?.let(::mapQuestSet)

    override fun getRecentQuestSets(limit: Int): List<WeeklyQuestSet> {
        if (limit <= 0) return emptyList()
        return storage.connection.getResults(
            "SELECT week_id, starts_at, ends_at FROM weekly_quest_sets ORDER BY starts_at DESC LIMIT ?",
            limit
        ).map(::mapQuestSet)
    }

    override fun saveActiveQuestSet(questSet: WeeklyQuestSet) {
        storage.connection.connection.use { connection ->
            connection.committingTransaction {
                val lock = if (mariaDb) " FOR UPDATE" else ""
                val previousWeek = connection.selectOne(
                    "SELECT week_id FROM weekly_quest_sets WHERE active = 1$lock"
                ) { it.getString(1) }
                val transition = connection.selectOne("SELECT week_id FROM guild_bank_quest_transition WHERE id = 1$lock") { it.getString(1) }
                check(transition == null || (transition == previousWeek && previousWeek != questSet.weekId)) {
                    "Cannot replace quest definitions during a weekly transition"
                }
                if (previousWeek != null && previousWeek != questSet.weekId) {
                    checkNoUnresolvedBankIntents(connection, previousWeek)
                }
                check(connection.updateStatement("UPDATE guild_bank_quest_revision SET revision = revision + 1 WHERE id = 1") == 1) {
                    "Bank quest revision is unavailable"
                }
                connection.updateStatement("UPDATE weekly_quest_sets SET active = 0 WHERE active = 1")
                connection.updateStatement(
                    questSetUpsertSql,
                    questSet.weekId,
                    questSet.startsAt.toEpochMilli(),
                    questSet.endsAt.toEpochMilli()
                )
                connection.updateStatement(
                    "DELETE FROM weekly_quest_definitions WHERE week_id = ?",
                    questSet.weekId
                )
                questSet.quests.forEachIndexed { index, quest ->
                    saveDefinition(connection, questSet.weekId, index, quest)
                }
                connection.updateStatement("DELETE FROM guild_bank_quest_transition WHERE id = 1")
            }
        }
    }

    override fun deactivateActiveQuestSet() {
        storage.connection.connection.use { connection ->
            connection.committingTransaction {
                val lock = if (mariaDb) " FOR UPDATE" else ""
                connection.selectOne("SELECT week_id FROM weekly_quest_sets WHERE active = 1$lock") { it.getString(1) }
                    ?.let {
                        checkNoUnresolvedBankIntents(connection, it)
                        check(connection.updateStatement("UPDATE guild_bank_quest_revision SET revision = revision + 1 WHERE id = 1") == 1) {
                            "Bank quest revision is unavailable"
                        }
                    }
                connection.updateStatement("UPDATE weekly_quest_sets SET active = 0 WHERE active = 1")
                connection.updateStatement("DELETE FROM guild_bank_quest_transition WHERE id = 1")
            }
        }
    }

    private fun checkNoUnresolvedBankIntents(connection: Connection, weekId: String) {
        val unresolved = unresolvedBankIntent(connection, weekId, lockRows = true)
        check(unresolved == null) { "Weekly quest transition blocked by unresolved bank contribution $unresolved" }
    }

    override fun hasUnresolvedBankContributions(weekId: String): Boolean =
        storage.connection.connection.use { unresolvedBankIntent(it, weekId, lockRows = false) != null }

    override fun beginBankQuestTransition(weekId: String): Boolean = beginBankQuestTransitionInternal(weekId, null)

    override fun beginBankQuestTransition(weekId: String, expectedRevision: Long): Boolean {
        require(expectedRevision >= 0)
        return beginBankQuestTransitionInternal(weekId, expectedRevision)
    }

    private fun beginBankQuestTransitionInternal(weekId: String, expectedRevision: Long?): Boolean =
        storage.connection.connection.use { connection ->
            connection.committingTransaction {
                val lock = if (mariaDb) " FOR UPDATE" else ""
                val active = connection.selectOne("SELECT week_id FROM weekly_quest_sets WHERE active = 1$lock") { it.getString(1) }
                if (active != weekId) return@committingTransaction false
                if (expectedRevision != null) {
                    val revision = checkNotNull(connection.selectOne(
                        "SELECT revision FROM guild_bank_quest_revision WHERE id = 1$lock"
                    ) { it.getLong(1) }) { "Bank quest revision is unavailable" }
                    if (revision != expectedRevision) return@committingTransaction false
                }
                if (unresolvedBankIntent(connection, weekId, lockRows = true) != null) return@committingTransaction false
                val existing = connection.selectOne("SELECT week_id FROM guild_bank_quest_transition WHERE id = 1$lock") { it.getString(1) }
                if (existing != null) return@committingTransaction existing == weekId
                connection.updateStatement("INSERT INTO guild_bank_quest_transition (id, week_id) VALUES (1, ?)", weekId)
                true
            }
        }

    private fun unresolvedBankIntent(connection: Connection, weekId: String, lockRows: Boolean): String? {
        val lock = if (mariaDb && lockRows) " FOR UPDATE" else ""
        return connection.selectOne(
            """SELECT i.transaction_id FROM guild_bank_quest_intents i
               JOIN guild_gold_operations o ON o.transaction_id = i.transaction_id
               WHERE i.week_id = ? AND o.status IN ('PREPARED', 'BALANCE_APPLIED', 'APPLIED', 'FAILED_COMPENSATION')
                 AND NOT EXISTS (SELECT 1 FROM guild_bank_quest_recovery a WHERE a.transaction_id = i.transaction_id)
                 AND NOT EXISTS (SELECT 1 FROM guild_bank_contribution_quests done
                     WHERE done.transaction_id = i.transaction_id AND done.quest_id = i.quest_id)
               LIMIT 1$lock""".trimIndent(), weekId,
        ) { it.getString(1) }
    }

    private fun saveDefinition(
        statement: Connection,
        weekId: String,
        order: Int,
        quest: QuestDefinition
    ) {
        val target = quest.target
        val firstCondition = quest.conditions.firstOrNull()
        statement.updateStatement(
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
            value.completedAt?.toEpochMilli(),
            value.claimActorId?.toString(),
            if (value.rewardDelivered) 1 else 0,
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

    override fun getClaimedProgress(weekId: String): List<GuildQuestProgress> =
        storage.connection.getResults(
            "SELECT * FROM guild_quest_progress WHERE week_id = ? AND claimed = 1",
            weekId,
        ).map(::mapProgress)

    override fun getUnclaimedCompletedProgress(weekId: String): List<GuildQuestProgress> =
        storage.connection.getResults(
            """
            SELECT * FROM guild_quest_progress
            WHERE week_id = ? AND claimed = 0 AND completed_at IS NOT NULL
            ORDER BY completed_at ASC, quest_id ASC, guild_id ASC
            """.trimIndent(),
            weekId,
        ).map(::mapProgress)

    override fun getPendingClaimRewards(): List<GuildQuestProgress> =
        storage.connection.getResults(
            "SELECT * FROM guild_quest_progress WHERE claimed = 1 AND reward_delivered = 0"
        ).map(::mapProgress)

    override fun tryMarkClaimed(
        weekId: String,
        questId: String,
        guildId: UUID,
        actorId: UUID?,
    ): Boolean =
        storage.connection.executeUpdate(
            """
            UPDATE guild_quest_progress
            SET claimed = 1, claim_actor_id = ?, reward_delivered = 0
            WHERE week_id = ? AND quest_id = ? AND guild_id = ? AND claimed = 0
            """.trimIndent(),
            actorId?.toString(),
            weekId,
            questId,
            guildId.toString(),
        ) == 1

    override fun markClaimRewardDelivered(
        weekId: String,
        questId: String,
        guildId: UUID,
    ): Boolean =
        storage.connection.executeUpdate(
            """
            UPDATE guild_quest_progress
            SET reward_delivered = 1
            WHERE week_id = ? AND quest_id = ? AND guild_id = ? AND claimed = 1
            """.trimIndent(),
            weekId,
            questId,
            guildId.toString(),
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

    override fun markLeaderboardRecipientPaid(
        weekId: String,
        questId: String,
        guildId: UUID,
    ): Boolean {
        storage.connection.executeUpdate(
            payoutInsertSql,
            weekId,
            questId,
            guildId.toString(),
        )
        return isLeaderboardRecipientPaid(weekId, questId, guildId)
    }

    override fun deleteWeekProgress(weekId: String) {
        storage.connection.executeUpdate(
            """
            DELETE FROM guild_quest_progress
            WHERE week_id = ? AND NOT (claimed = 1 AND reward_delivered = 0)
            """.trimIndent(),
            weekId,
        )
        // Keep weekly-bonus markers as durable idempotency history.
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
        completedAt = row.nullableLong("completed_at")?.let(Instant::ofEpochMilli),
        claimActorId = row.nullableString("claim_actor_id")?.let(UUID::fromString),
        rewardDelivered = row.getInt("reward_delivered") == 1,
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
    private companion object {
        const val VERIFIED_BANK_CONTRIBUTION_SQL = """
            SELECT r.fresh_after - r.fresh_before
            FROM guild_bank_contribution_receipts r
            JOIN guild_gold_operations o ON o.transaction_id = r.transaction_id
            JOIN guild_gold_external_attempts e ON e.transaction_id = o.transaction_id
            JOIN guild_bank_xp_deliveries d ON d.transaction_id = o.transaction_id
            JOIN weekly_quest_sets w ON w.week_id = ?
            JOIN weekly_quest_definitions q ON q.week_id = w.week_id AND q.quest_id = ?
            WHERE r.transaction_id = ? AND r.guild_id = ? AND o.guild_id = r.guild_id AND o.actor_id = ?
              AND o.status = 'APPLIED' AND o.direction = 'CREDIT'
              AND o.route IN ('PERSONAL_ACCOUNT', 'PHYSICAL_ITEM') AND e.phase = 'CONFIRMED' AND e.purpose = 'DEPOSIT'
              AND d.capture_denial IS NULL AND d.prestige_count = ?
              AND (d.chapter_id = ? OR (d.chapter_id IS NULL AND ? IS NULL))
              AND d.occurred_at >= w.starts_at AND d.occurred_at < w.ends_at
              AND w.active = 1 AND q.action = 'DEPOSIT_BANK' AND q.target_count = ?
        """
    }
}
