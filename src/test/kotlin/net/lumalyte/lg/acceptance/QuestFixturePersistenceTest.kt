package net.lumalyte.lg.acceptance

import co.aikar.idb.Database
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardSqlTestFixture
import net.lumalyte.lg.infrastructure.persistence.migrations.QuestSchema
import net.lumalyte.lg.infrastructure.persistence.migrations.BankContributionSchema
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildGoldSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/** The real definition transaction must preserve unrelated paid quest state. */
class QuestFixturePersistenceTest : RewardSqlTestFixture() {
    private val guild = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val original = WeeklyQuestSet("fixture-cleanup-week", Instant.parse("2026-10-26T00:00:00Z"),
        Instant.parse("2026-11-02T00:00:00Z"), (1..2).map { index ->
            QuestDefinition("original-$index", action = QuestAction.MINE_BLOCKS,
                target = QuestTarget("minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, 100),
                targetCount = 50, tier = QuestRewardTier.COMMON, experienceReward = 10)
        })

    @Test fun `install and restore retain persisted claimed progress bonus and payout markers`() {
        val storage = prepared()
        val repository = QuestRepositorySQLite(storage)
        val before = persistedHash(storage)
        repository.saveActiveQuestSet(QuestFixturePlan.install(original))
        restore(repository)
        // A new repository and fresh JDBC connection prove committed data, not cached objects.
        assertEquals(original, QuestRepositorySQLite(storage).getActiveQuestSet())
        assertEquals(before, persistedHash(storage))
        val progress = assertNotNull(repository.getProgress(original.weekId, "original-1", guild))
        assertTrue(progress.claimed && progress.rewardDelivered)
        assertEquals(actor, progress.claimActorId)
        assertTrue(repository.isWeeklyBonusAwarded(original.weekId, guild))
        assertTrue(repository.isLeaderboardRecipientPaid(original.weekId, "original-1", guild))
    }

    @Test fun `failed fixture insert rolls back all definition changes and retains paid state`() {
        val storage = prepared()
        val repository = QuestRepositorySQLite(storage)
        val before = persistedHash(storage)
        rejectDefinition(storage, QuestFixturePlan.ID)
        assertFails { repository.saveActiveQuestSet(QuestFixturePlan.install(original)) }
        assertEquals(before, persistedHash(storage))
        assertEquals(original, repository.getActiveQuestSet())
    }

    @Test fun `failed cleanup retains complete fixture and paid state then permits retry`() {
        val storage = prepared()
        val repository = QuestRepositorySQLite(storage)
        val before = persistedHash(storage)
        repository.saveActiveQuestSet(QuestFixturePlan.install(original))
        val installed = persistedHash(storage)
        rejectDefinition(storage, "original-1")
        assertFails { restore(repository) }
        assertEquals(installed, persistedHash(storage))
        storage.connection.executeUpdate("DROP TRIGGER reject_fixture_definition")
        restore(repository)
        assertEquals(before, persistedHash(storage))
    }

    private fun prepared(): Storage<Database> = openStorage().also { storage ->
        storage.connection.connection.use {
            val mariaDb = storage.dialect == SqlDialect.MARIADB
            QuestSchema.create(it, mariaDb)
            GuildGoldSchema.create(it, mariaDb)
            BankContributionSchema.create(it, mariaDb)
        }
        QuestRepositorySQLite(storage).apply {
            saveActiveQuestSet(original)
            saveProgress(GuildQuestProgress(original.weekId, "original-1", guild, 50, true,
                Instant.parse("2026-10-26T01:00:00Z"), actor, true))
            tryMarkWeeklyBonusAwarded(original.weekId, guild)
            markLeaderboardRecipientPaid(original.weekId, "original-1", guild)
        }
    }

    private fun restore(repository: QuestRepositorySQLite) {
        repository.saveActiveQuestSet(QuestFixturePlan.restore(assertNotNull(repository.getActiveQuestSet()),
            original.weekId, QuestFixturePlan.fingerprint(original)))
    }

    private fun rejectDefinition(storage: Storage<Database>, id: String) {
        require(id == QuestFixturePlan.ID || id == "original-1")
        val body = if (storage.dialect == SqlDialect.MARIADB)
            "FOR EACH ROW BEGIN IF NEW.quest_id = '$id' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected fixture write failure'; END IF; END"
        else "WHEN NEW.quest_id = '$id' BEGIN SELECT RAISE(ABORT, 'injected fixture write failure'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_fixture_definition BEFORE INSERT ON weekly_quest_definitions $body")
    }

    private fun persistedHash(storage: Storage<Database>): String {
        val bytes = buildString {
            storage.connection.connection.use { connection ->
                listOf("weekly_quest_sets", "weekly_quest_definitions", "guild_quest_progress",
                    "guild_quest_weekly_bonus", "quest_leaderboard_payouts").forEach { table ->
                    append(table)
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT * FROM $table ORDER BY 1, 2").use { rows ->
                            while (rows.next()) for (column in 1..rows.metaData.columnCount) {
                                val value = rows.getString(column)
                                append(value?.length ?: -1).append(':').append(value.orEmpty()).append(';')
                            }
                        }
                    }
                }
            }
        }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
