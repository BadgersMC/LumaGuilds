package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.PlayerNotificationPreferenceRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.WarNotificationRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.DailyWarCostsService
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.application.services.GuildDisbandAnnouncementService
import net.lumalyte.lg.application.services.GuildGoldService
import net.lumalyte.lg.application.services.GuildLoginNotificationService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.WarNotificationService
import net.lumalyte.lg.application.services.WarPaymentService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.infrastructure.persistence.guilds.WarNotificationRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.WarRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.DailyWarCostsServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildDisbandAnnouncementServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildLoginNotificationServiceBukkit
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import net.lumalyte.lg.infrastructure.services.ToastSender
import net.lumalyte.lg.infrastructure.services.WarNotificationServiceBukkit
import net.lumalyte.lg.infrastructure.services.WarServiceBukkit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class NexusWarBindingsTest {

    private val expectedNames = setOf(
        "warRepository", "warNotificationRepository",
        "guildDisbandAnnouncementService", "warNotificationService", "guildLoginNotificationService",
        "warPaymentService", "warService", "dailyWarCostsService",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    /** Storage double: DDL and queries hit a relaxed database (empty results); no live DB. */
    private fun stubStorage(): Storage<Database> {
        val storage = mockk<Storage<Database>>()
        every { storage.connection } returns mockk<Database>(relaxed = true)
        every { storage.dialect } returns SqlDialect.SQLITE
        return storage
    }

    /** Plugin double exposing only the startup timestamp used by login notifications. */
    private fun pluginStartedAt(millis: Long): LumaGuilds {
        val plugin = mockk<LumaGuilds>()
        every { plugin.enabledAtMillis } returns millis
        return plugin
    }

    /** Registers mocks for every non-war dependency (owned by other tranches). */
    private fun registerWarDependencies(context: NexusContext, plugin: LumaGuilds = pluginStartedAt(0L)) {
        context.registerBean("plugin", LumaGuilds::class, plugin)
        context.registerBean("storage", Storage::class, stubStorage())
        context.registerBean("configService", ConfigService::class, mockk<ConfigService>())
        context.registerBean("langService", LangService::class, mockk<LangService>(relaxed = true))
        context.registerBean("toastSender", ToastSender::class, mockk<ToastSender>())
        context.registerBean(
            "playerNotificationPreferenceRepository", PlayerNotificationPreferenceRepository::class,
            mockk<PlayerNotificationPreferenceRepository>(),
        )
        context.registerBean("guildRepository", GuildRepository::class, mockk<GuildRepository>())
        context.registerBean("memberRepository", MemberRepository::class, mockk<MemberRepository>())
        context.registerBean("memberService", MemberService::class, mockk<MemberService>())
        context.registerBean("guildService", GuildService::class, mockk<GuildService>())
        context.registerBean("bankService", BankService::class, mockk<BankService>())
        context.registerBean("guildGoldService", GuildGoldService::class, mockk<GuildGoldService>())
        context.registerBean("progressionRepository", ProgressionRepository::class, mockk<ProgressionRepository>())
        context.registerBean(
            "progressionConfigService", ProgressionConfigService::class, mockk<ProgressionConfigService>(),
        )
        context.registerBean(
            "chapterTwoGuildAwardService", ChapterTwoGuildAwardService::class, mockk<ChapterTwoGuildAwardService>(),
        )
        context.registerBean("progressionService", ProgressionService::class, mockk<ProgressionService>())
        context.registerBean("seasonalEloCoordinator", SeasonalEloCoordinator::class, mockk<SeasonalEloCoordinator>())
        context.registerBean("guildActionCoordinator", GuildActionCoordinator::class, GuildActionCoordinator())
    }

    @Test
    fun `registers exactly the war tranche by contract name`() = withContext { context ->
        val before = context.getBeanNames().toSet()
        context.registerWarBindings()
        val added = context.getBeanNames().toSet() - before

        assertEquals(8, expectedNames.size)
        assertEquals(expectedNames, added)
    }

    @Test
    fun `leaves quest, toast, leaderboard and provenance definitions to other tranches`() =
        withContext { context ->
            context.registerWarBindings()
            val names = context.getBeanNames()

            listOf(
                "questCompletionNotificationRepository", "playerNotificationPreferenceRepository",
                "toastSender", "questCompletionNotifier", "questService", "questRewardSink",
                "questProgressListener", "leaderboardService", "blockProvenanceOperationQueue",
                "webApiServer", "guildLeaderboardHandler",
                // Redundant concrete aliases would make contract lookups ambiguous.
                "warRepositorySQL", "warServiceBukkit",
            ).forEach { assertFalse(it in names, "$it must not be registered by the war tranche") }
        }

    @Test
    fun `registration is lazy and touches neither storage, configuration nor plugin`() =
        withContext { context ->
            val plugin = mockk<LumaGuilds>()
            val storage = mockk<Storage<*>>()
            val config = mockk<ConfigService>()
            context.registerBean("plugin", LumaGuilds::class, plugin)
            context.registerBean("storage", Storage::class, storage)
            context.registerBean("configService", ConfigService::class, config)

            context.registerWarBindings()

            // Strict mocks: DDL, a war probe, a config read or enabledAtMillis read would be recorded.
            confirmVerified(plugin, storage, config)
        }

    @Test
    fun `war repositories are singletons under their contracts`() = withContext { context ->
        context.registerBean("storage", Storage::class, stubStorage())
        context.registerWarBindings()

        val wars = context.getBean<WarRepository>()
        assertIs<WarRepositorySQL>(wars)
        assertSame(wars, context.getBean<WarRepository>())

        val notifications = context.getBean<WarNotificationRepository>()
        assertIs<WarNotificationRepositorySQL>(notifications)
        assertSame(notifications, context.getBean<WarNotificationRepository>())
    }

    @Test
    fun `notification services are singletons with the legacy implementations`() = withContext { context ->
        registerWarDependencies(context)
        context.registerWarBindings()

        val disband = context.getBean<GuildDisbandAnnouncementService>()
        assertIs<GuildDisbandAnnouncementServiceBukkit>(disband)
        assertSame(disband, context.getBean<GuildDisbandAnnouncementService>())

        val war = context.getBean<WarNotificationService>()
        assertIs<WarNotificationServiceBukkit>(war)
        assertSame(war, context.getBean<WarNotificationService>())
    }

    @Test
    fun `login notifications read the plugin startup timestamp once on construction`() =
        withContext { context ->
            val plugin = pluginStartedAt(1_234L)
            registerWarDependencies(context, plugin)
            context.registerWarBindings()

            val login = context.getBean<GuildLoginNotificationService>()
            assertIs<GuildLoginNotificationServiceBukkit>(login)
            assertSame(login, context.getBean<GuildLoginNotificationService>())

            verify(exactly = 1) { plugin.enabledAtMillis }
        }

    @Test
    fun `war payment, war and daily cost services share one singleton graph`() = withContext { context ->
        registerWarDependencies(context)
        context.registerWarBindings()

        val payments = context.getBean<WarPaymentService>()
        assertSame(payments, context.getBean<WarPaymentService>())

        // WarServiceBukkit.init probes warRepository.getAll(); the relaxed database returns no rows.
        val wars = context.getBean<WarService>()
        assertIs<WarServiceBukkit>(wars)
        assertSame(wars, context.getBean<WarService>())

        val dailyCosts = context.getBean<DailyWarCostsService>()
        assertIs<DailyWarCostsServiceBukkit>(dailyCosts)
        assertSame(dailyCosts, context.getBean<DailyWarCostsService>())
    }
}
