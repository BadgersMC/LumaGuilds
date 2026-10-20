package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.api.events.GuildBankDepositEvent
import net.lumalyte.lg.api.events.GuildLevelChangedEvent
import net.lumalyte.lg.api.events.GuildLevelUpEvent
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.BankDepositXpDeliveryService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PermanentExperienceService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.guilds.BankDepositDeliveryFixture
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Actual delivery receipts compose with the live Bukkit adapters; unrelated services are inert. */
class BankDepositPublicationTest : BankDepositDeliveryFixture() {
    @TempDir lateinit var directory: Path
    private lateinit var plugin: Plugin
    private lateinit var cached: ProgressionRepositorySQLite
    private lateinit var progression: ProgressionServiceBukkit
    private lateinit var guilds: GuildRepository
    private lateinit var publisher: BankDepositXpDeliveryService
    private val publicationHints = mutableListOf<Int?>()
    private val events = PublicationEvents()
    private val config = object : ConfigService {
        override fun loadConfig() = MainConfig()
    }

    override fun newStorage() = VirtualThreadSQLiteStorage(directory.toFile())

    @BeforeEach fun setupPublication() {
        val server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        server.pluginManager.registerEvents(events, plugin)
        guilds = mockk(relaxed = true) {
            every { getById(guild) } returns Guild(guild, "Bank delivery", createdAt = occurredAt)
        }
        cached = ProgressionRepositorySQLite(db, config)
        assertEquals(0, cached.getGuildProgression(guild)?.totalExperience)
        val awards = ExperienceAwardRepositorySQL(db, ProgressionCurve.from(config.loadConfig().progression))
        val activity = object : PlaytimeActivityService {
            override fun isXpBlocked(playerId: java.util.UUID) = false
        }
        progression = ProgressionServiceBukkit(
            progressionRepository = cached,
            guildRepository = guilds,
            memberRepository = mockk(relaxed = true),
            configService = config,
            progressionConfigService = mockk(relaxed = true),
            plugin = plugin,
            lang = mockk(relaxed = true),
            permanentExperienceService = PermanentExperienceService(awards, activity),
            experienceAwardRepository = awards,
        )
        publisher = BankDepositXpDeliveryService(delivery, publish = { guildId, level, acknowledged ->
            publicationHints += level
            progression.onCommittedBankDepositExperience(guildId, level, acknowledged)
        })
    }

    @AfterEach fun closePublication() {
        MockBukkit.unmock()
    }

    @Test fun failedAcknowledgementRetainsCacheRetryWithoutRepeatingLevelEvents() {
        quote = quote.copy(policy = originalPolicy.copy(awardXp = 5_000, capXp = 10_000))
        val request = personalRequest()
        assertIs<GuildGoldResult.Applied>(service.depositPersonal(request))
        execute(
            "CREATE TRIGGER reject_bank_cache_ack BEFORE UPDATE ON guild_bank_xp_deliveries " +
                "WHEN OLD.cache_pending = 1 AND NEW.cache_pending = 0 " +
                "BEGIN SELECT RAISE(ABORT, 'cache acknowledgement unavailable'); END",
        )

        val first = assertNotNull(publisher.deliver(request.transactionId))

        assertEquals(BankDepositXpStatus.AWARDED, first.status)
        assertTrue(first.awardedNow)
        val level = assertNotNull(first.leveledUpTo)
        assertEquals(5_000, cached.getGuildProgression(guild)?.totalExperience)
        assertEquals(listOf(guild), events.levelChanges.map { it.guildId })
        assertEquals(listOf(level), events.levelChanges.map { it.newLevel })
        assertEquals(listOf(guild), events.levelUps.map { it.guildId })
        assertEquals(listOf(request.transactionId), delivery.pendingCache(10))
        assertAccounting(100, 100, 5_000, 1)
        execute("DROP TRIGGER reject_bank_cache_ack")

        assertEquals(1, publisher.retryCache(10))

        assertEquals(listOf<Int?>(level, null), publicationHints)
        assertEquals(1, events.levelChanges.size)
        assertEquals(1, events.levelUps.size)
        assertTrue(events.deposits.isEmpty())
        assertTrue(delivery.pendingCache(10).isEmpty())
        assertFalse(assertNotNull(delivery.complete(request.transactionId)).cachePending)
        assertAccounting(100, 100, 5_000, 1)
    }

    @Test fun xpDeliveryFailureStillPublishesTheLiveDepositEventOnce() {
        val bank = BankServiceBukkit(
            bankRepository = mockk(relaxed = true),
            memberRepository = mockk(relaxed = true),
            rankRepository = mockk(relaxed = true),
            progressionRepository = cached,
            progressionConfigService = mockk(relaxed = true),
            configService = config,
            guildRepository = guilds,
            guildService = mockk(relaxed = true),
            vaultInventoryManager = mockk(relaxed = true),
            bankDepositXpDeliveryService = publisher,
            goldService = service,
        )
        val request = physicalRequest()
        rejectInsert("experience_transactions")

        val applied = assertIs<GuildGoldResult.Applied>(bank.depositPhysical(request))

        assertEquals(request.transactionId, applied.transactionId)
        assertEquals(0L, applied.oldBalance)
        assertEquals(100L, applied.newBalance)
        assertEquals("READY", deliveryStatus(request.transactionId))
        assertEquals(listOf(guild), events.deposits.map { it.guildId })
        assertEquals(listOf(actor), events.deposits.map { it.playerId })
        assertEquals(listOf(100), events.deposits.map { it.amount })
        assertAccounting(100, null, 0, 0)
        allowDeliveryWrites()

        assertEquals(1, publisher.drain(10))

        assertEquals(1, events.deposits.size)
        assertEquals(1, cached.getGuildProgression(guild)?.totalExperience)
        assertTrue(delivery.pending(10).isEmpty())
        assertTrue(delivery.pendingCache(10).isEmpty())
        assertEquals(100L, physical.consumed)
        assertEquals(1, physical.reserveCalls)
        assertAccounting(100, 100, 1, 1)
    }

    class PublicationEvents : Listener {
        val levelChanges = mutableListOf<GuildLevelChangedEvent>()
        val levelUps = mutableListOf<GuildLevelUpEvent>()
        val deposits = mutableListOf<GuildBankDepositEvent>()

        @EventHandler fun onLevelChanged(event: GuildLevelChangedEvent) { levelChanges += event }
        @EventHandler fun onLevelUp(event: GuildLevelUpEvent) { levelUps += event }
        @EventHandler fun onDeposit(event: GuildBankDepositEvent) { deposits += event }
    }
}
