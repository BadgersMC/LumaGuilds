package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
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
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.DailyWarCostsServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildDisbandAnnouncementServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildLoginNotificationServiceBukkit
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import net.lumalyte.lg.infrastructure.services.ToastSender
import net.lumalyte.lg.infrastructure.services.WarNotificationServiceBukkit
import net.lumalyte.lg.infrastructure.services.WarServiceBukkit
import kotlin.reflect.KClass

/**
 * Nexus tranche for the war + related-notification definitions of the legacy Koin
 * `progressionModule` (Modules.kt: WarRepository .. WarService, DailyWarCostsService).
 *
 * Every definition is a lazy singleton registered under its contract type, mirroring the
 * Koin `single<Contract> { Impl(...) }` shapes, with constructor arguments in the exact
 * legacy order. Nothing is constructed at registration time: no SQL DDL
 * (WarRepositorySQL.init), no `warRepository.getAll()` probe (WarServiceBukkit.init), no
 * `LumaGuilds.enabledAtMillis` read and no Bukkit API.
 *
 * Preserved semantics:
 *  - WarServiceBukkit receives all 14 named dependencies, including the optional ones
 *    (ChapterTwoGuildAwardService, SeasonalEloCoordinator, WarNotificationService,
 *    MemberRepository) resolved as REQUIRED, exactly like Koin `get()` did. Committed XP
 *    still flows through ChapterTwoGuildAwardService's own lazy ProgressionService callback
 *    (NexusProgressionBindings); this tranche does not reorder it.
 *  - WarServiceBukkit shares the GuildActionCoordinator singleton (never its default
 *    `GuildActionCoordinator()`), so war and guild actions keep one lock domain.
 *  - GuildLoginNotificationServiceBukkit.startupAtMillis reads `LumaGuilds.enabledAtMillis`
 *    when the singleton is first constructed, as the Koin factory did. Its remaining
 *    defaulted parameters (clock, player lookup, head factory) keep their defaults.
 *  - WarNotificationServiceBukkit receives the LumaGuilds instance as its Plugin.
 *
 * Cross-tranche dependencies resolved lazily here but registered by other tranches:
 *  - ToastSender, PlayerNotificationPreferenceRepository (notifications/quest tranche)
 *  - GuildGoldService, BankService (NexusEconomyBindings)
 *  - ChapterTwoGuildAwardService, ProgressionService, ProgressionConfigService,
 *    SeasonalEloCoordinator, GuildActionCoordinator, ProgressionRepository
 *    (NexusProgressionBindings)
 *  - GuildRepository, MemberRepository, MemberService, GuildService (NexusGuildCoreBindings)
 *
 * Intentionally NOT registered here (owned by another tranche): quest completion
 * notifications, notification preferences, ToastSender, QuestCompletionNotifier,
 * QuestService, QuestRewardSink, QuestProgressListener, LeaderboardService, the block
 * provenance queue and non-war web services.
 */
internal fun NexusContext.registerWarBindings() {
    // --- Repositories ---
    warSingleton<WarRepository> { WarRepositorySQL(warStorage()) }
    warSingleton<WarNotificationRepository> { WarNotificationRepositorySQL(warStorage()) }

    // --- Notifications ---
    warSingleton<GuildDisbandAnnouncementService> {
        GuildDisbandAnnouncementServiceBukkit(
            getBean<MemberService>(),
            getBean<LangService>(),
            getBean<ToastSender>(),
        )
    }
    warSingleton<WarNotificationService> {
        WarNotificationServiceBukkit(
            getBean<LumaGuilds>(),
            getBean<WarNotificationRepository>(),
            getBean<GuildRepository>(),
            getBean<MemberRepository>(),
            getBean<WarRepository>(),
            getBean<LangService>(),
            getBean<ToastSender>(),
        )
    }
    warSingleton<GuildLoginNotificationService> {
        GuildLoginNotificationServiceBukkit(
            getBean<MemberService>(),
            getBean<PlayerNotificationPreferenceRepository>(),
            getBean<LangService>(),
            getBean<ToastSender>(),
            startupAtMillis = getBean<LumaGuilds>().enabledAtMillis,
        )
    }

    // --- War services ---
    warSingleton<WarPaymentService> {
        WarPaymentService(getBean<WarRepository>(), getBean<GuildGoldService>())
    }
    warSingleton<WarService> {
        WarServiceBukkit(
            configService = getBean<ConfigService>(),
            bankService = getBean<BankService>(),
            progressionRepository = getBean<ProgressionRepository>(),
            progressionConfigService = getBean<ProgressionConfigService>(),
            chapterTwoGuildAwardService = getBean<ChapterTwoGuildAwardService>(),
            progressionService = getBean<ProgressionService>(),
            warRepository = getBean<WarRepository>(),
            warPayments = getBean<WarPaymentService>(),
            memberService = getBean<MemberService>(),
            guildRepository = getBean<GuildRepository>(),
            seasonalElo = getBean<SeasonalEloCoordinator>(),
            warNotifications = getBean<WarNotificationService>(),
            memberRepository = getBean<MemberRepository>(),
            guildActions = getBean<GuildActionCoordinator>(),
        )
    }
    warSingleton<DailyWarCostsService> {
        DailyWarCostsServiceBukkit(
            getBean<WarService>(),
            getBean<GuildService>(),
            getBean<ConfigService>(),
            getBean<BankService>(),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun NexusContext.warStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.warSingleton(noinline factory: () -> T) {
    registerBean(warBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun warBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
