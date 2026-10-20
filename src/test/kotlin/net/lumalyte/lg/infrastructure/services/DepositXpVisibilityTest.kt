package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PermanentExperienceService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.GuildProgression
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.guilds.BankProgressionRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildShopXpRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Real SQL award receipts and the real cached menu repository share one committed total. */
internal class DepositXpVisibilityTest {
    /** Isolated database directory supplied by JUnit. */
    @TempDir var directory: Path? = null
    private var storage: VirtualThreadSQLiteStorage by Delegates.notNull()
    private var cached: ProgressionRepositorySQLite by Delegates.notNull()
    private var awards: ExperienceAwardRepositorySQL by Delegates.notNull()
    private var bank: ChapterTwoGuildAwardService by Delegates.notNull()
    private var progression: ProgressionServiceBukkit by Delegates.notNull()
    private var sink: QuestRewardSinkBukkit by Delegates.notNull()
    private val guildId = UUID.randomUUID()

    /** Warm the menu cache before any award. */
    @BeforeEach fun setup() {
        MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        storage = VirtualThreadSQLiteStorage(checkNotNull(directory).toFile())
        storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY, level INTEGER NOT NULL DEFAULT 1)")
        storage.connection.executeUpdate("INSERT INTO guilds (id, level) VALUES (?, 1)", guildId.toString())
        val config = object : ConfigService { override fun loadConfig() = MainConfig() }
        val curve = ProgressionCurve.from(config.loadConfig().progression)
        cached = ProgressionRepositorySQLite(storage, config)
        cached.saveGuildProgression(GuildProgression.create(guildId, curve.experienceForNextLevel(1)))
        awards = ExperienceAwardRepositorySQL(storage, curve)
        val activity = object : PlaytimeActivityService { override fun isXpBlocked(playerId: UUID) = false }
        val permanent = PermanentExperienceService(awards, activity)
        progression =
            ProgressionServiceBukkit(
                cached,
                mockk(relaxed = true),
                mockk(relaxed = true),
                config,
                mockk(relaxed = true),
                plugin,
                mockk(relaxed = true),
                permanent,
                awards,
            )
        bank =
            ChapterTwoGuildAwardService(
                BankProgressionRepositorySQL(storage),
                mockk(relaxed = true),
                permanent,
                config,
                activity,
                onCommittedExperience = progression::onCommittedExperience,
            )
        sink = QuestRewardSinkBukkit(progression, plugin)
    }

    /** Close SQL workers before releasing the isolated server. */
    @AfterEach fun cleanup() {
        storage.connection.close(CLOSE_SECONDS, TimeUnit.SECONDS)
        MockBukkit.unmock()
    }

    /** Bank and quest XP become visible without permitting replay. */
    @Test fun depositsAndQuestShowXp() {
        assertEquals(0, cached.getGuildProgression(guildId)?.totalExperience)
        val actor = UUID.randomUUID()
        val result = bank.awardBankGrowth(guildId, actor, 0, BANK_AMOUNT)
        assertTrue(result is net.lumalyte.lg.domain.entities.ExperienceAwardResult.Awarded, "$result")
        assertEquals(1, persistedXp())
        assertEquals(1, cached.getGuildProgression(guildId)?.totalExperience)

        val questReceipt = UUID.randomUUID()
        assertTrue(sink.awardExperience(guildId, QUEST_XP, questReceipt))
        assertEquals(TOTAL_XP, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(1, cached.getGuildProgression(guildId)?.currentLevel)
        assertTrue(sink.awardExperience(guildId, QUEST_XP, questReceipt))
        assertEquals(TOTAL_XP, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(2, receipts())

        bank.awardBankGrowth(guildId, actor, BANK_AMOUNT, 0)
        bank.awardBankGrowth(guildId, actor, 0, BANK_AMOUNT)
        assertEquals(TOTAL_XP, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(2, receipts())
    }

    /** A rolled-back XP write must not permanently consume the deposit's growth. */
    @Test fun bankAwardRetryAfterSqlFailurePreservesGrowth() {
        val actor = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-10-20T12:00:00Z")
        storage.connection.executeUpdate(
            "CREATE TRIGGER reject_bank_xp BEFORE INSERT ON experience_transactions " +
                "BEGIN SELECT RAISE(ABORT, 'bank XP receipt rejected'); END",
        )

        assertFailsWith<SQLException> {
            bank.awardBankGrowth(guildId, actor, 0, BANK_AMOUNT, occurredAt)
        }
        assertEquals(0, persistedXp())
        assertEquals(0, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(0, receipts())
        assertTrue(awards.getAwardedXpByPool(guildId, occurredAt).isEmpty())
        storage.connection.executeUpdate("DROP TRIGGER reject_bank_xp")

        val result = bank.awardBankGrowth(guildId, actor, 0, BANK_AMOUNT, occurredAt)

        assertTrue(result is net.lumalyte.lg.domain.entities.ExperienceAwardResult.Awarded, "$result")
        assertEquals(1, persistedXp())
        assertEquals(1, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(1, receipts())
        assertEquals(1, awards.getAwardedXpByPool(guildId, occurredAt)["BANK_DEPOSIT"])

        bank.awardBankGrowth(guildId, actor, 0, BANK_AMOUNT, occurredAt)
        assertEquals(1, persistedXp())
        assertEquals(1, receipts())
    }

    /** Replaying a committed receipt only repairs cached state. */
    @Test fun rewardReplayRefreshesCache() {
        val receipt = UUID.randomUUID()
        awards.awardAtomically(questRequest(receipt), questPolicy(), QUEST_XP, null)
        assertEquals(QUEST_XP, persistedXp())
        assertEquals(0, cached.getGuildProgression(guildId)?.totalExperience)

        assertTrue(sink.awardExperience(guildId, QUEST_XP, receipt))

        assertEquals(QUEST_XP, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(1, receipts())
    }

    /** A completed shop receipt repairs a missed publication without another award. */
    @Test fun shopRewardReplayRefreshesCache() {
        createShopTables()
        val repository = GuildShopXpRepositorySQL(storage, awards)
        val sale = GuildShopXpSale(UUID.randomUUID(), guildId, UUID.randomUUID(), Instant.now().toEpochMilli())
        repository.prepare(sale, GuildShopXpPolicy(enabled = true))
        assertEquals("AWARDED:$SHOP_XP", repository.complete(sale.id).status)
        assertEquals(SHOP_XP, persistedXp())
        assertEquals(0, cached.getGuildProgression(guildId)?.totalExperience)

        val provider = GuildShopXpProvider(repository, mockk(relaxed = true), progression)
        assertEquals("AWARDED:$SHOP_XP", provider.complete(sale.id))

        assertEquals(SHOP_XP, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(SHOP_XP, persistedXp())
        assertEquals(1, receipts())
    }

    /** Numeric and legacy text timestamps retain their exact meanings. */
    @Test fun mixedTimestampsRemainReadable() {
        val instant = Instant.parse("2026-08-28T12:00:00Z")
        setTimestamps(instant.toEpochMilli(), instant.toString(), instant.toEpochMilli())
        val mixed = checkNotNull(cached.refreshGuildProgression(guildId))
        assertEquals(instant, mixed.createdAt)
        assertEquals(instant, mixed.lastUpdated)
        assertEquals(instant, mixed.lastLevelUp)

        setTimestamps(instant.toString(), instant.toEpochMilli(), null)
        val reversed = checkNotNull(cached.refreshGuildProgression(guildId))
        assertEquals(instant, reversed.createdAt)
        assertEquals(instant, reversed.lastUpdated)
        assertEquals(null, reversed.lastLevelUp)
    }

    private fun questPolicy() =
        ExperiencePolicy(ExperienceSource.WEEKLY_ACTIVITY, "WEEKLY_ACTIVITY", 1, 0, CapPeriod.UNLIMITED, true)

    private fun createShopTables() {
        storage.connection.executeUpdate("CREATE TABLE members (player_id VARCHAR(36), guild_id VARCHAR(36))")
        storage.connection.executeUpdate(
            "CREATE TABLE guild_reward_accounts (guild_id VARCHAR(36) PRIMARY KEY, prestige_count INT)",
        )
        storage.connection.executeUpdate("INSERT INTO guild_reward_accounts VALUES (?, 0)", guildId.toString())
        storage.connection.executeUpdate(
            "CREATE TABLE chapter_lifecycle " +
                "(chapter_id VARCHAR(64) PRIMARY KEY, phase VARCHAR(32), starts_at BIGINT, ends_at BIGINT)",
        )
    }

    private fun questRequest(receipt: UUID): ExperienceAwardRequest {
        return ExperienceAwardRequest(
            guildId,
            null,
            ExperienceSource.WEEKLY_ACTIVITY,
            QUEST_XP,
            Instant.now(),
            transactionId = receipt,
        )
    }

    private fun setTimestamps(created: Any, updated: Any, lastLevel: Any?) {
        storage.connection.executeUpdate(
            "UPDATE guild_progression SET created_at = ?, last_updated = ?, last_level_up = ? WHERE guild_id = ?",
            created,
            updated,
            lastLevel,
            guildId.toString(),
        )
    }

    private fun receipts(): Int {
        return checkNotNull(
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS count FROM experience_transactions WHERE guild_id = ?",
                guildId.toString(),
            ),
        ).getInt("count")
    }

    private fun persistedXp(): Int {
        return checkNotNull(
            storage.connection.getFirstRow(
                "SELECT total_experience FROM guild_progression WHERE guild_id = ?",
                guildId.toString(),
            ),
        ).getInt("total_experience")
    }

    private companion object {
        const val BANK_AMOUNT = 100L
        const val QUEST_XP = 500
        const val SHOP_XP = 5
        const val TOTAL_XP = QUEST_XP + 1
        const val CLOSE_SECONDS = 5L
    }
}
