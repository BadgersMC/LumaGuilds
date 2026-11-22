package net.lumalyte.lg.di.nexus

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.QuestCompletionNotificationRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.QuestCompletionNotifier
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.LeaderboardServiceBukkit
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.QuestCompletionNotifierBukkit
import net.lumalyte.lg.infrastructure.services.ToastSender
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class NexusQuestBindingsTest {

    private val expectedNames = setOf(
        // Notifications
        "questCompletionNotificationRepository", "playerNotificationPreferenceRepository",
        "toastSender", "questCompletionNotifier",
        // Quests
        "questRewardSink", "questService", "questTargetCatalog",
        "questAvailabilityNotifierBukkit", "weeklyQuestCoordinator", "questProgressListener",
        // Leaderboards
        "leaderboardService",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    @Test
    fun `registers the quest notification and leaderboard tranche by contract name`() = withContext { context ->
        val before = context.getBeanNames().toSet()
        context.registerQuestBindings()
        val added = context.getBeanNames().toSet() - before

        assertEquals(11, expectedNames.size)
        assertEquals(expectedNames, added)
    }

    @Test
    fun `leaves war, announcement, web and executor-owned bindings to other tranches`() = withContext { context ->
        context.registerQuestBindings()
        val names = context.getBeanNames()

        listOf(
            "warRepository", "warNotificationRepository", "warNotificationService", "warPaymentService",
            "warService", "dailyWarCostsService", "guildDisbandAnnouncementService",
            "guildLoginNotificationService", "blockProvenanceOperationQueue",
            "guildLeaderboardProjectionService", "guildLeaderboardHandler", "webApiServer",
            // Already owned by NexusProgressionBindings.
            "progressionConfigService", "questRepository", "leaderboardRepository",
        ).forEach { assertFalse(it in names, "unexpected bean $it") }
    }

    @Test
    fun `registration is lazy and touches no storage, configuration or plugin`() = withContext { context ->
        val storage = mockk<Storage<*>>()
        val config = mockk<ConfigService>()
        val progressionConfig = mockk<ProgressionConfigService>()
        val plugin = mockk<LumaGuilds>()
        context.registerBean("storage", Storage::class, storage)
        context.registerBean("configService", ConfigService::class, config)
        context.registerBean("progressionConfigService", ProgressionConfigService::class, progressionConfig)
        context.registerBean("plugin", LumaGuilds::class, plugin)

        context.registerQuestBindings()

        // Strict mocks: any schema init, config snapshot or plugin access at registration would be recorded.
        confirmVerified(storage, config, progressionConfig, plugin)
    }

    @Test
    fun `leaderboard service is one production singleton`() = withContext { context ->
        context.registerBean("leaderboardRepository", LeaderboardRepository::class, mockk<LeaderboardRepository>())
        context.registerQuestBindings()

        val service = context.getBean<LeaderboardService>()
        assertIs<LeaderboardServiceBukkit>(service)
        assertSame(service, context.getBean<LeaderboardService>())
    }

    /** External (non-tranche) dependencies of the notifier chain; no live Bukkit or database. */
    private fun registerNotifierDependencies(context: NexusContext, plugin: LumaGuilds) {
        context.registerBean("plugin", LumaGuilds::class, plugin)
        context.registerBean("storage", Storage::class, mockk<Storage<*>>())
        context.registerBean("memberService", MemberService::class, mockk<MemberService>())
        context.registerBean("langService", LangService::class, mockk<LangService>())
        context.registerBean("questRepository", QuestRepository::class, mockk<QuestRepository>())
    }

    @Test
    fun `completion notifier chain builds as singletons without touching Bukkit`() = withContext { context ->
        registerNotifierDependencies(context, mockk<LumaGuilds>())
        context.registerQuestBindings()

        val notifier = context.getBean<QuestCompletionNotifier>()
        assertIs<QuestCompletionNotifierBukkit>(notifier)
        assertSame(notifier, context.getBean<QuestCompletionNotifier>())
        assertSame(context.getBean<ToastSender>(), context.getBean<ToastSender>())
        assertSame(
            context.getBean<QuestCompletionNotificationRepository>(),
            context.getBean<QuestCompletionNotificationRepository>(),
        )
    }

    @Test
    fun `quest service snapshots quest reward settings at construction`() = withContext { context ->
        val progressionConfig = mockk<ProgressionConfigService>()
        every { progressionConfig.getProgressionConfig().quests.fullSetBonusXp } returns 1_234
        every { progressionConfig.getProgressionConfig().quests.leaderboardWinnerXp } returns 5_678
        val plugin = mockk<LumaGuilds>(relaxed = true)
        every { plugin.name } returns "LumaGuilds" // QuestRewardSinkBukkit builds NamespacedKeys.
        registerNotifierDependencies(context, plugin)
        context.registerBean("progressionConfigService", ProgressionConfigService::class, progressionConfig)
        context.registerBean("progressionService", ProgressionService::class, mockk<ProgressionService>())
        context.registerQuestBindings()

        val service = context.getBean<QuestService>()
        assertEquals(1_234, service.fullSetBonusExperience)
        assertSame(service, context.getBean<QuestService>())
    }
}
