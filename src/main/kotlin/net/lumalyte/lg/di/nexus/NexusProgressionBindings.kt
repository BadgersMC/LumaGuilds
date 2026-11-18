package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.BankDepositXpRepository
import net.lumalyte.lg.application.persistence.BankProgressionRepository
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.persistence.ChronicleGoalRepository
import net.lumalyte.lg.application.persistence.GuildListRepository
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.PrestigeRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.persistence.RewardStateRepository
import net.lumalyte.lg.application.persistence.SpawnBannerRepository
import net.lumalyte.lg.application.services.BankDepositXpDeliveryService
import net.lumalyte.lg.application.services.BankDepositXpQuotes
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.application.services.CombatService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.application.services.GuildChronicleService
import net.lumalyte.lg.application.services.GuildPrestigeAnnouncement
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.application.services.GuildRewardPurchaseAccess
import net.lumalyte.lg.application.services.GuildRewardPurchaseService
import net.lumalyte.lg.application.services.GuildRewardService
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ModeService
import net.lumalyte.lg.application.services.PermanentExperienceService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.domain.rewards.RewardReadSettings
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.guilds.BankDepositXpRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.BankProgressionRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.BlockProvenanceRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.ChronicleGoalRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildListRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildShopXpRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.KillRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.LeaderboardRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PrestigeRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.QuestRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardOwnershipRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardPurchaseRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.RewardStateRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.SpawnBannerRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.CombatServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildAchievementRuntime
import net.lumalyte.lg.infrastructure.services.GuildPrestigeAnnouncementBukkit
import net.lumalyte.lg.infrastructure.services.KillServiceBukkit
import net.lumalyte.lg.infrastructure.services.ModeServiceBukkit
import net.lumalyte.lg.infrastructure.services.PlaytimeActivityServiceBukkit
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.ProgressionServiceBukkit
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import org.bukkit.plugin.Plugin
import java.util.logging.Level
import kotlin.reflect.KClass

/**
 * First Nexus tranche for the legacy Koin `progressionModule` (Modules.kt).
 *
 * Covers the rewards/prestige/chronicle block, progression + XP repositories, bank XP
 * delivery, and the core progression services, plus the four dependencies those
 * services need from later in the module: SeasonalEloCoordinator, ProgressionService,
 * ModeService (for CombatService) and ProgressionConfigService.
 *
 * Every definition is a lazy singleton. Nothing is constructed at registration time, so
 * no SQL schema init runs before storage migrations and no Bukkit API is touched.
 *
 * Concrete/interface aliases: the legacy graph registered RewardOwnershipRepositorySQL and
 * ExperienceAwardRepositorySQL as concrete singletons plus a second interface alias that
 * delegated to them. Nexus indexes an implementation under its direct interfaces, so ONE
 * concrete registration serves both lookups with a single instance. Registering the
 * redundant alias would make `getBean<Interface>()` ambiguous (see InviteBlockServiceImpl
 * in NexusGuildCoreBindings).
 *
 * Constructor cycles preserved as lazy lookups (resolved on use, never at construction):
 *  - ChapterTwoGuildAwardService.onCommittedExperience -> ProgressionService
 *  - BankDepositXpDeliveryService.publish -> ProgressionService (ProgressionService/BankService cycle)
 *  - BankDepositXpQuotes.questRevision -> QuestRepository
 *  - GuildRewardService's RewardStateRepository adapter -> RewardStateRepositorySQL
 *  - Config providers (XP curve, prestige config, reward settings, xp boost) read ConfigService
 *    per call so reloads are observed exactly as before.
 *
 * Intentionally NOT registered here (later tranches):
 *  - BlockProvenanceOperationQueue: needs a java.util.concurrent.Executor. Nexus exposes its
 *    virtual-thread CoroutineDispatcher, not the old named Koin ExecutorService, and a
 *    stand-alone executor must not be allocated. Needs a Nexus-owned Executor bridge first.
 *    NexusProgressionListenerBindings' ProgressionEventListener cannot resolve until then.
 *  - Economy-owned dependencies resolved lazily here but not yet in Nexus:
 *    GuildGoldRepositorySQL (RewardPurchaseRepositorySQL, PrestigeRepositorySQL) and
 *    GuildGoldService (GuildRewardPurchaseService).
 *  - War: WarRepository, WarNotificationRepository, WarNotificationService, WarPaymentService,
 *    WarService, DailyWarCostsService.
 *  - Notifications: QuestCompletionNotificationRepository, PlayerNotificationPreferenceRepository,
 *    ToastSender, QuestCompletionNotifier (Bukkit main-thread bridge),
 *    GuildDisbandAnnouncementService, GuildLoginNotificationService.
 *  - Quests: QuestRewardSink, QuestService, QuestTargetCatalog, QuestAvailabilityNotifierBukkit,
 *    WeeklyQuestCoordinator, QuestProgressListener.
 *  - Leaderboards/web/directory: LeaderboardService, GuildLeaderboardProjectionService,
 *    GuildListService (named VirtualThreadExecutor), GuildDiscordProfileService,
 *    SpawnBannerServiceBukkit, SpawnBannerListener, GuildLeaderboardHandler, WebApiServer.
 */
internal fun NexusContext.registerProgressionBindings() {
    // --- Rewards, chronicle and prestige ---
    progressionSingleton<GuildAchievementRuntime> {
        GuildAchievementRuntime(getBean<LumaGuilds>(), getBean<MemberRepository>(), getBean<ProgressionRepository>())
    }
    progressionSingleton<GuildActionCoordinator> { GuildActionCoordinator() }
    progressionSingleton<RewardCatalog> { RewardCatalog.chapterTwo() }
    progressionSingleton<ChronicleGoalRepository> { ChronicleGoalRepositorySQL(progressionStorage()) }
    progressionSingleton<GuildChronicleService> {
        val members = getBean<MemberService>()
        val rewards = getBean<GuildRewardService>()
        GuildChronicleService(
            getBean<RewardCatalog>(),
            getBean<ChronicleGoalRepository>(),
            { actor, guild -> members.getMember(actor, guild) != null },
            { actor, guild -> members.hasPermission(actor, guild, RankPermission.MANAGE_GUILD_SETTINGS) },
            rewards::read,
        )
    }
    progressionSingleton<RewardPurchaseRepositorySQL> {
        RewardPurchaseRepositorySQL(
            progressionStorage(),
            getBean<RewardCatalog>(),
            getBean<GuildGoldRepositorySQL>(),
            getBean<GuildActionCoordinator>(),
        )
    }
    progressionSingleton<GuildRewardPurchaseAccess> { GuildRewardPurchaseAccess(getBean(), getBean()) }
    progressionSingleton<GuildRewardPurchaseService> {
        val config = getBean<ConfigService>()
        val access = getBean<GuildRewardPurchaseAccess>()
        GuildRewardPurchaseService(
            getBean<GuildRewardService>(),
            getBean(),
            { config.loadConfig().chapterTwoRewardsEnabled },
            access::allowed,
        )
    }
    // Also serves getBean<RewardOwnershipRepository>() via Nexus interface indexing.
    progressionSingleton<RewardOwnershipRepositorySQL> {
        RewardOwnershipRepositorySQL(progressionStorage(), getBean<RewardCatalog>())
    }
    progressionSingleton<PrestigeRepository> {
        PrestigeRepositorySQL(
            progressionStorage(),
            getBean<RewardCatalog>(),
            getBean<RewardOwnershipRepositorySQL>(),
            getBean<GuildGoldRepositorySQL>(),
            getBean<GuildActionCoordinator>(),
        ) { getBean<ConfigService>().loadConfig() }
    }
    progressionSingleton<GuildPrestigeAnnouncement> {
        GuildPrestigeAnnouncementBukkit(getBean(), getBean<LangService>())
    }
    progressionSingleton<GuildPrestigeService> {
        GuildPrestigeService(
            getBean<RewardOwnershipRepositorySQL>(),
            getBean<ProgressionRepository>(),
            getBean<RewardCatalog>(),
            getBean<PrestigeRepository>(),
            getBean<GuildRewardPurchaseAccess>(),
            getBean<ConfigService>(),
            getBean<GuildPrestigeAnnouncement>(),
        )
    }
    progressionSingleton<RewardStateRepositorySQL> {
        RewardStateRepositorySQL(progressionStorage(), getBean<RewardOwnershipRepositorySQL>())
    }
    progressionSingleton<GuildRewardService> {
        val config = getBean<ConfigService>()
        GuildRewardService(
            RewardStateRepository { guildId -> getBean<RewardStateRepositorySQL>().read(guildId) },
            getBean<RewardCatalog>(),
        ) {
            val current = config.loadConfig()
            RewardReadSettings(
                current.chapterTwoRewardsEnabled,
                current.bank.maxBankBalance.toLong(),
                current.guild.maxMembersPerGuild,
            )
        }
    }

    // --- Repositories ---
    progressionSingleton<KillRepository> { KillRepositorySQLite(progressionStorage()) }
    progressionSingleton<ProgressionRepository> {
        ProgressionRepositorySQLite(progressionStorage(), getBean<ConfigService>())
    }
    // Also serves getBean<ExperienceAwardRepository>() via Nexus interface indexing.
    progressionSingleton<ExperienceAwardRepositorySQL> {
        ExperienceAwardRepositorySQL(progressionStorage()) {
            ProgressionCurve.from(getBean<ConfigService>().loadConfig().progression)
        }
    }
    progressionSingleton<GuildShopXpRepository> {
        GuildShopXpRepositorySQL(
            progressionStorage(),
            getBean<ExperienceAwardRepositorySQL>(),
            getBean<GuildActionCoordinator>(),
        )
    }
    progressionSingleton<BankProgressionRepository> {
        BankProgressionRepositorySQL(progressionStorage(), getBean<ExperienceAwardRepositorySQL>())
    }
    progressionSingleton<BankDepositXpRepository> {
        BankDepositXpRepositorySQL(progressionStorage(), getBean<ExperienceAwardRepositorySQL>())
    }
    progressionSingleton<BankDepositXpQuotes> {
        BankDepositXpQuotes(
            getBean<ConfigService>(),
            getBean<PlaytimeActivityService>(),
            questRevision = { getBean<QuestRepository>().currentBankQuestRevision() },
        )
    }
    progressionSingleton<BankDepositXpDeliveryService> {
        BankDepositXpDeliveryService(
            getBean<BankDepositXpRepository>(),
            publish = { guildId, level, acknowledged ->
                // Resolve on publication, avoiding the ProgressionService/BankService constructor cycle.
                getBean<ProgressionService>().onCommittedBankDepositExperience(guildId, level, acknowledged)
            },
            onFailure = { transactionId, error ->
                getBean<LumaGuilds>().logger.log(
                    Level.WARNING, "Bank XP delivery or cache publication remains pending for $transactionId", error,
                )
            },
        )
    }
    progressionSingleton<LeaderboardRepository> { LeaderboardRepositorySQLite(progressionStorage()) }
    progressionSingleton<GuildListRepository> { GuildListRepositorySQL(progressionStorage()) }
    progressionSingleton<SpawnBannerRepository> { SpawnBannerRepositorySQL(progressionStorage()) }
    progressionSingleton<QuestRepository> { QuestRepositorySQLite(progressionStorage()) }
    progressionSingleton<BlockProvenanceRepository> { BlockProvenanceRepositorySQLite(progressionStorage()) }
    // BlockProvenanceOperationQueue deliberately omitted; see file KDoc.

    // --- Services ---
    progressionSingleton<KillService> { KillServiceBukkit(getBean()) }
    progressionSingleton<ModeService> { ModeServiceBukkit(getBean(), getBean(), getBean(), getBean()) }
    progressionSingleton<CombatService> { CombatServiceBukkit(getBean(), getBean(), getBean(), getBean()) }
    progressionSingleton<PlaytimeActivityService> { PlaytimeActivityServiceBukkit() }
    progressionSingleton<PermanentExperienceService> {
        val config = getBean<ConfigService>()
        PermanentExperienceService(getBean<ExperienceAwardRepositorySQL>(), getBean<PlaytimeActivityService>()) {
            config.loadConfig().progression.xpBoost
        }
    }
    progressionSingleton<ChapterTwoGuildAwardService> {
        ChapterTwoGuildAwardService(
            getBean<BankProgressionRepository>(),
            getBean(),
            getBean<PermanentExperienceService>(),
            getBean<ConfigService>(),
            getBean<PlaytimeActivityService>(),
            onCommittedExperience = { guildId, level ->
                // Resolve only after an award, avoiding a constructor cycle with ProgressionService.
                runCatching { getBean<ProgressionService>().onCommittedExperience(guildId, level) }
                    .onFailure { error ->
                        getBean<LumaGuilds>().logger.log(
                            Level.WARNING, "Failed to publish committed guild XP for $guildId", error,
                        )
                    }
            },
        )
    }
    progressionSingleton<SeasonalEloCoordinator> {
        SeasonalEloCoordinator(progressionStorage(), getBean<ConfigService>())
    }
    progressionSingleton<ProgressionConfigService> { ProgressionConfigService(getBean<Plugin>()) }
    progressionSingleton<ProgressionService> {
        ProgressionServiceBukkit(
            getBean<ProgressionRepository>(),
            getBean(),
            getBean<MemberRepository>(),
            getBean<ConfigService>(),
            getBean<ProgressionConfigService>(),
            getBean<LumaGuilds>(),
            getBean<LangService>(),
            getBean<PermanentExperienceService>(),
            getBean<ExperienceAwardRepositorySQL>(),
            getBean<GuildRewardService>(),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun NexusContext.progressionStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.progressionSingleton(noinline factory: () -> T) {
    registerBean(progressionBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun progressionBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
