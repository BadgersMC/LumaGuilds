package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.PlayerNotificationPreferenceRepository
import net.lumalyte.lg.application.persistence.QuestCompletionNotificationRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.QuestCompletionNotifier
import net.lumalyte.lg.application.services.QuestRewardSink
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.services.QuestTargetCatalog
import net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue
import net.lumalyte.lg.infrastructure.listeners.QuestProgressListener
import net.lumalyte.lg.infrastructure.persistence.guilds.PlayerNotificationPreferenceRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.QuestCompletionNotificationRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.BukkitQuestTargetProvider
import net.lumalyte.lg.infrastructure.services.LeaderboardServiceBukkit
import net.lumalyte.lg.infrastructure.services.NexoQuestTargetProvider
import net.lumalyte.lg.infrastructure.services.PacketEventsToastSender
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.QuestAvailabilityNotifierBukkit
import net.lumalyte.lg.infrastructure.services.QuestCompletionNotifierBukkit
import net.lumalyte.lg.infrastructure.services.QuestRewardSinkBukkit
import net.lumalyte.lg.infrastructure.services.ToastSender
import net.lumalyte.lg.infrastructure.services.WeeklyQuestCoordinator
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Quest / quest-notification / simple leaderboard tranche of the legacy Koin
 * `progressionModule` (Modules.kt).
 *
 * Every definition is a lazy singleton: nothing is constructed at registration, so no SQL is
 * touched before storage migrations, no config is snapshotted early and no Bukkit API is called.
 * Bukkit is only reached from runtime callbacks (main-thread bridge, weekly reset broadcast),
 * exactly as in the Koin graph.
 *
 * Preserved behaviour:
 *  - QuestCompletionNotifier: runs inline on the primary thread, otherwise hops to the Bukkit
 *    scheduler via callSyncMethod and blocks at most 5 seconds.
 *  - QuestService: fullSetBonusXp / leaderboardWinnerXp snapshotted from ProgressionConfigService
 *    when the service is first constructed (not per call), as before.
 *  - WeeklyQuestCoordinator: resolves QuestAvailabilityNotifierBukkit when the coordinator is
 *    built and broadcasts to Bukkit.getOnlinePlayers() only when a new quest set is created.
 *
 * Dependencies resolved from other tranches: QuestRepository, LeaderboardRepository,
 * BlockProvenanceRepository, ProgressionConfigService, ProgressionService (progression);
 * MemberService (guild core); LangService / plugin (foundation).
 *
 * QuestProgressListener is registered lazily but cannot resolve until the coordinator-owned
 * BlockProvenanceOperationQueue (Executor bridge) exists in Nexus — same as ProgressionEventListener.
 *
 * Intentionally NOT registered here: war beans (WarRepository, WarNotificationRepository,
 * WarNotificationService, WarPaymentService, WarService, DailyWarCostsService) and
 * GuildDisbandAnnouncementService / GuildLoginNotificationService (war tranche);
 * GuildLeaderboardProjectionService, GuildListService (named VirtualThreadExecutor),
 * GuildDiscordProfileService, SpawnBannerServiceBukkit, SpawnBannerListener,
 * GuildLeaderboardHandler and WebApiServer (web/directory tranche).
 */
internal fun NexusContext.registerQuestBindings() {
    // --- Notifications ---
    questSingleton<QuestCompletionNotificationRepository> { QuestCompletionNotificationRepositorySQL(questStorage()) }
    questSingleton<PlayerNotificationPreferenceRepository> { PlayerNotificationPreferenceRepositorySQL(questStorage()) }
    questSingleton<ToastSender> { PacketEventsToastSender(getBean<LumaGuilds>()) }
    questSingleton<QuestCompletionNotifier> {
        val plugin = getBean<LumaGuilds>()
        QuestCompletionNotifierBukkit(
            getBean<MemberService>(),
            getBean<LangService>(),
            getBean<ToastSender>(),
            getBean<QuestCompletionNotificationRepository>(),
            getBean<QuestRepository>(),
            onMainThread = { action ->
                if (Bukkit.isPrimaryThread()) action()
                else Bukkit.getScheduler().callSyncMethod(plugin) { action() }
                    .get(5, TimeUnit.SECONDS)
            },
        )
    }

    // --- Leaderboards ---
    questSingleton<LeaderboardService> { LeaderboardServiceBukkit(getBean<LeaderboardRepository>()) }

    // --- Quests ---
    questSingleton<QuestRewardSink> { QuestRewardSinkBukkit(getBean<ProgressionService>(), getBean<LumaGuilds>()) }
    questSingleton<QuestService> {
        val questConfig = getBean<ProgressionConfigService>().getProgressionConfig().quests
        QuestService(
            repository = getBean<QuestRepository>(),
            rewards = getBean<QuestRewardSink>(),
            fullSetBonusExperience = questConfig.fullSetBonusXp,
            leaderboardWinnerExperience = questConfig.leaderboardWinnerXp,
            completionNotifier = getBean<QuestCompletionNotifier>(),
        )
    }
    questSingleton<QuestTargetCatalog> {
        QuestTargetCatalog(
            listOf(
                BukkitQuestTargetProvider(getBean<Plugin>()),
                NexoQuestTargetProvider(getBean<Plugin>()),
            )
        )
    }
    questSingleton<QuestAvailabilityNotifierBukkit> {
        QuestAvailabilityNotifierBukkit(
            getBean<QuestService>(),
            getBean<MemberService>(),
            getBean<ProgressionConfigService>(),
            getBean<LangService>(),
        )
    }
    questSingleton<WeeklyQuestCoordinator> {
        val notifier = getBean<QuestAvailabilityNotifierBukkit>()
        WeeklyQuestCoordinator(
            getBean<QuestService>(),
            getBean<ProgressionConfigService>(),
            getBean<QuestTargetCatalog>(),
        ) {
            notifier.onReset(Bukkit.getOnlinePlayers())
        }
    }

    // --- Listeners ---
    questSingleton<QuestProgressListener> {
        QuestProgressListener(
            getBean<QuestService>(),
            getBean<MemberService>(),
            getBean<BlockProvenanceRepository>(),
            getBean<BlockProvenanceOperationQueue>(),
            getBean<Plugin>(),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun NexusContext.questStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.questSingleton(noinline factory: () -> T) {
    registerBean(questBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun questBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
