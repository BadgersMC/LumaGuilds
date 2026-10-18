package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
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
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.guilds.BankProgressionRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real SQL award receipts and the real cached menu repository share one committed total. */
class DepositXpVisibilityTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var cached: ProgressionRepositorySQLite
    private lateinit var awards: ExperienceAwardRepositorySQL
    private lateinit var bank: ChapterTwoGuildAwardService
    private lateinit var sink: QuestRewardSinkBukkit
    private val guildId = UUID.randomUUID()

    @BeforeEach fun setup() {
        MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY, level INTEGER NOT NULL DEFAULT 1)")
        storage.connection.executeUpdate("INSERT INTO guilds (id, level) VALUES (?, 1)", guildId.toString())
        val config = object : ConfigService { override fun loadConfig() = MainConfig() }
        val curve = ProgressionCurve.from(config.loadConfig().progression)
        cached = ProgressionRepositorySQLite(storage, config)
        cached.saveGuildProgression(GuildProgression.create(guildId, curve.experienceForNextLevel(1)))
        awards = ExperienceAwardRepositorySQL(storage, curve)
        val activity = object : PlaytimeActivityService { override fun isXpBlocked(playerId: UUID) = false }
        val permanent = PermanentExperienceService(awards, activity)
        val progression = ProgressionServiceBukkit(
            cached, mockk(relaxed = true), mockk(relaxed = true), config,
            mockk(relaxed = true), plugin, mockk(relaxed = true), permanent, awards,
        )
        bank = ChapterTwoGuildAwardService(
            BankProgressionRepositorySQL(storage), mockk(relaxed = true), permanent, config, activity,
            onCommittedExperience = progression::onCommittedExperience,
        )
        sink = QuestRewardSinkBukkit(progression, plugin)
    }

    @AfterEach fun cleanup() {
        storage.connection.close(5, TimeUnit.SECONDS)
        MockBukkit.unmock()
    }

    @Test fun `ordinary deposit and quest reward update warm menu cache without a level-up`() {
        assertEquals(0, cached.getGuildProgression(guildId)?.totalExperience)
        val actor = UUID.randomUUID()
        val result = bank.awardBankGrowth(guildId, actor, 0, 100)
        assertTrue(result is net.lumalyte.lg.domain.entities.ExperienceAwardResult.Awarded, "$result")
        assertEquals(1, persistedXp())
        assertEquals(1, cached.getGuildProgression(guildId)?.totalExperience)

        val questReceipt = UUID.randomUUID()
        assertTrue(sink.awardExperience(guildId, 500, questReceipt))
        assertEquals(501, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(1, cached.getGuildProgression(guildId)?.currentLevel)
        assertTrue(sink.awardExperience(guildId, 500, questReceipt))
        assertEquals(501, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(2, receipts())

        bank.awardBankGrowth(guildId, actor, 100, 0)
        bank.awardBankGrowth(guildId, actor, 0, 100)
        assertEquals(501, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(2, receipts())
    }

    @Test fun `quest receipt replay repairs a stale cache without another persisted award`() {
        val receipt = UUID.randomUUID()
        val policy = ExperiencePolicy(ExperienceSource.WEEKLY_ACTIVITY, "WEEKLY_ACTIVITY", 1, 0,
            CapPeriod.UNLIMITED, true)
        awards.awardAtomically(ExperienceAwardRequest(guildId, null, ExperienceSource.WEEKLY_ACTIVITY,
            500, Instant.now(), transactionId = receipt), policy, 500, null)
        assertEquals(500, persistedXp())
        assertEquals(0, cached.getGuildProgression(guildId)?.totalExperience)

        assertTrue(sink.awardExperience(guildId, 500, receipt))

        assertEquals(500, cached.getGuildProgression(guildId)?.totalExperience)
        assertEquals(1, receipts())
    }

    @Test fun `cache refresh reads numeric and legacy text timestamps including null level-up`() {
        val instant = Instant.parse("2026-08-28T12:00:00Z")
        storage.connection.executeUpdate(
            "UPDATE guild_progression SET created_at = ?, last_updated = ?, last_level_up = ? WHERE guild_id = ?",
            instant.toEpochMilli(), instant.toString(), instant.toEpochMilli(), guildId.toString(),
        )
        val mixed = cached.refreshGuildProgression(guildId)!!
        assertEquals(instant, mixed.createdAt)
        assertEquals(instant, mixed.lastUpdated)
        assertEquals(instant, mixed.lastLevelUp)

        storage.connection.executeUpdate(
            "UPDATE guild_progression SET created_at = ?, last_updated = ?, last_level_up = NULL WHERE guild_id = ?",
            instant.toString(), instant.toEpochMilli(), guildId.toString(),
        )
        val reversed = cached.refreshGuildProgression(guildId)!!
        assertEquals(instant, reversed.createdAt)
        assertEquals(instant, reversed.lastUpdated)
        assertEquals(null, reversed.lastLevelUp)
    }

    private fun receipts(): Int = storage.connection.getFirstRow(
        "SELECT COUNT(*) AS count FROM experience_transactions WHERE guild_id = ?", guildId.toString(),
    )!!.getInt("count")

    private fun persistedXp(): Int = storage.connection.getFirstRow(
        "SELECT total_experience FROM guild_progression WHERE guild_id = ?", guildId.toString(),
    )!!.getInt("total_experience")
}
