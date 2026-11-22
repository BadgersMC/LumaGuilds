package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.GuildGoldRepository
import net.lumalyte.lg.application.persistence.GuildListRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.KillRepository
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.application.persistence.SpawnBannerRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildDiscordProfileService
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.infrastructure.listeners.SpawnBannerListener
import net.lumalyte.lg.infrastructure.services.GuildLeaderboardProjectionService
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit
import net.lumalyte.lg.infrastructure.web.WebApiServer
import net.lumalyte.lg.infrastructure.web.handlers.GuildLeaderboardHandler
import org.bukkit.Bukkit
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Web / leaderboard-projection / directory tranche of the legacy Koin `progressionModule`
 * (Modules.kt, the block after LeaderboardService).
 *
 * Every definition is a lazy singleton. Registration constructs nothing, snapshots no
 * config, touches no storage and starts no scheduler/HTTP server: the projection timer,
 * spawn-banner refresh timer and WebApiServer only start when their owner calls `start()`
 * during the final cutover, exactly as in the Koin graph.
 *
 * Preserved behaviour:
 *  - GuildLeaderboardProjectionService: ClaimRepository is optional (Koin `getOrNull`). The
 *    claims graph is only registered when claims are enabled, so it is looked up by name with
 *    containsBean at first resolve and passed as null otherwise.
 *  - GuildDiscordProfileService: the Bukkit offline-player name lookup stays inside the
 *    resolver callback; it is never invoked at construction.
 *  - GuildLeaderboardHandler / WebApiServer: each snapshots `loadConfig().webApi` once when it
 *    is first resolved (one call per singleton, as Koin did), never per request.
 *  - WebApiServer shares the single GuildLeaderboardHandler singleton.
 *
 * Dependencies resolved from other tranches: GuildRepository, MemberRepository, GuildService,
 * MemberService, RankService, RelationService, GuildBannerService (guild core);
 * KillRepository, LeaderboardRepository, ProgressionRepository, SpawnBannerRepository,
 * GuildPrestigeService, SeasonalEloCoordinator, RewardOwnershipRepository via the
 * RewardOwnershipRepositorySQL interface index (progression); GuildGoldRepository via the
 * GuildGoldRepositorySQL interface index, BankService (economy); LeaderboardService (quest);
 * ClaimRepository (claims, optional); plugin, ConfigService, LangService (foundation).
 *
 * GuildListService accepts Executor, even though Koin previously supplied an ExecutorService.
 * It borrows Nexus's virtual-thread pool through nexusVirtualExecutor(), which rejects work
 * after scope cancellation and creates no independently owned executor.
 */
internal fun NexusContext.registerWebBindings() {
    webSingleton<GuildLeaderboardProjectionService> {
        GuildLeaderboardProjectionService(
            getBean<LumaGuilds>(),
            getBean<GuildRepository>(),
            getBean<MemberRepository>(),
            optionalWebBean<ClaimRepository>(CLAIM_REPOSITORY_BEAN),
            getBean<GuildGoldRepository>(),
            getBean<KillRepository>(),
            getBean<LeaderboardRepository>(),
        )
    }
    webSingleton<GuildListService> {
        GuildListService(
            repository = getBean<GuildListRepository>(),
            guildRepository = getBean<GuildRepository>(),
            configService = getBean<ConfigService>(),
            progressionConfigService = getBean<ProgressionConfigService>(),
            asyncExecutor = nexusVirtualExecutor(),
        )
    }
    webSingleton<GuildDiscordProfileService> {
        GuildDiscordProfileService(
            guildService = getBean<GuildService>(),
            memberService = getBean<MemberService>(),
            rankService = getBean<RankService>(),
            progressionRepository = getBean<ProgressionRepository>(),
            prestigeService = getBean<GuildPrestigeService>(),
            relationService = getBean<RelationService>(),
            playerNameResolver = { playerId: UUID -> Bukkit.getOfflinePlayer(playerId).name },
        )
    }
    webSingleton<SpawnBannerServiceBukkit> {
        SpawnBannerServiceBukkit(
            getBean<LumaGuilds>(),
            getBean<SpawnBannerRepository>(),
            getBean<LeaderboardRepository>(),
            getBean<GuildService>(),
            getBean<LangService>(),
        )
    }
    webSingleton<SpawnBannerListener> {
        SpawnBannerListener(getBean<SpawnBannerServiceBukkit>(), getBean<LangService>())
    }
    webSingleton<GuildLeaderboardHandler> {
        GuildLeaderboardHandler(
            leaderboardService = getBean<LeaderboardService>(),
            guildService = getBean<GuildService>(),
            memberService = getBean<MemberService>(),
            bannerService = getBean<GuildBannerService>(),
            progressionRepository = getBean<ProgressionRepository>(),
            leaderboardRepository = getBean<LeaderboardRepository>(),
            bankService = getBean<BankService>(),
            rewardOwnershipRepository = getBean<RewardOwnershipRepository>(),
            seasonalElo = getBean<SeasonalEloCoordinator>(),
            config = getBean<ConfigService>().loadConfig().webApi,
        )
    }
    webSingleton<WebApiServer> {
        WebApiServer(
            plugin = getBean<LumaGuilds>(),
            config = getBean<ConfigService>().loadConfig().webApi,
            guildLeaderboardHandler = getBean<GuildLeaderboardHandler>(),
        )
    }
}

/** Bean name used by NexusClaimsBindings; only present when claims are enabled. */
private const val CLAIM_REPOSITORY_BEAN = "claimRepository"

private inline fun <reified T : Any> NexusContext.optionalWebBean(name: String): T? =
    if (containsBean(name)) getBean(name) as T else null

private inline fun <reified T : Any> NexusContext.webSingleton(noinline factory: () -> T) {
    registerBean(webBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun webBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
