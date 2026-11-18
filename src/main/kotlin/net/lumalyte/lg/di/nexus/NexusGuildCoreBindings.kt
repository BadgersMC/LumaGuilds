package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.AuditRepository
import net.lumalyte.lg.application.persistence.GuildBannerRepository
import net.lumalyte.lg.application.persistence.GuildCosmeticUnlockRepository
import net.lumalyte.lg.application.persistence.GuildHomeAccessRepository
import net.lumalyte.lg.application.persistence.GuildHomeActivationRepository
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.PenaltyRepository
import net.lumalyte.lg.application.persistence.RankClaimPermissionProfileRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.persistence.StrikeRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.AuditService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.InvitationStatisticsService
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanRenderService
import net.lumalyte.lg.infrastructure.persistence.AuditRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildBannerRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildCosmeticUnlockRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildHomeAccessGrantRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildHomeActivationRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildInvitationRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.InviteBlockRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.MemberRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.MembershipHistoryRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.PenaltyRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RankClaimPermissionProfileRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.RankRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.RelationRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.guilds.StrikeRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.AdminOverrideServiceImpl
import net.lumalyte.lg.infrastructure.services.AuditServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildBannerServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildRolePermissionResolverBukkit
import net.lumalyte.lg.infrastructure.services.GuildServiceBukkit
import net.lumalyte.lg.infrastructure.services.InviteBlockServiceImpl
import net.lumalyte.lg.infrastructure.services.LfgServiceBukkit
import net.lumalyte.lg.infrastructure.services.MemberServiceBukkit
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.infrastructure.services.RankServiceBukkit
import net.lumalyte.lg.infrastructure.services.RelationServiceBukkit
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Manual, lazy Nexus replacements for the legacy guildsModule Koin graph.
 *
 * The factories are deliberately not instantiated at registration time. Some
 * service constructors depend on progression/vault/economy bindings that the
 * migration hasn't converted yet. This class does NOT activate the graph.
 */
internal fun NexusContext.registerGuildCoreBindings(claimsEnabled: Boolean) {
    guildSingleton<GuildRepository> {
        GuildRepositorySQLite(guildStorage()) {
            ProgressionCurve.from(getBean<ConfigService>().loadConfig().progression)
                .experienceForNextLevel(1)
        }
    }
    guildSingleton<GuildHomeActivationRepository> { GuildHomeActivationRepositorySQL(guildStorage()) }
    guildSingleton<GuildHomeAccessRepository> { GuildHomeAccessGrantRepositorySQL(guildStorage()) }
    guildSingleton<RankRepository> { RankRepositorySQLite(guildStorage()) }
    guildSingleton<RankClaimPermissionProfileRepository> {
        RankClaimPermissionProfileRepositorySQL(guildStorage())
    }
    guildSingleton<MemberRepository> { MemberRepositorySQLite(guildStorage()) }
    guildSingleton<RelationRepository> { RelationRepositorySQLite(guildStorage()) }
    guildSingleton<GuildInvitationRepository> { GuildInvitationRepositorySQLite(guildStorage()) }
    guildSingleton<InviteBlockRepository> { InviteBlockRepositorySQL(guildStorage()) }

    // A single implementation singleton is indexed by Nexus under both of its
    // direct interfaces: InviteBlockService and DurableInviteBlockService.
    // Registering redundant aliases would make getBean<Interface>() ambiguous.
    guildSingleton<InviteBlockServiceImpl> {
        InviteBlockServiceImpl(getBean(), getBean(), getBean())
    }
    guildSingleton<InvitationStatisticsService> { InvitationStatisticsService(getBean()) }
    guildSingleton<GuildBannerRepository> { GuildBannerRepositorySQLite(guildStorage()) }
    guildSingleton<AuditRepository> { AuditRepositorySQLite(guildStorage()) }
    guildSingleton<MembershipHistoryRepository> { MembershipHistoryRepositorySQLite(guildStorage()) }
    guildSingleton<GuildCosmeticUnlockRepository> { GuildCosmeticUnlockRepositorySQLite(guildStorage()) }
    guildSingleton<GuildCosmeticUnlockService> { GuildCosmeticUnlockService(getBean(), getBean()) }

    guildSingleton<AdminOverrideService> { AdminOverrideServiceImpl() }
    guildSingleton<NexoEmojiService> {
        NexoEmojiService(getBean<ConfigService>(), getBean<GuildCosmeticUnlockService>())
    }
    guildSingleton<MemberService> {
        MemberServiceBukkit(
            memberRepository = getBean(),
            rankRepository = getBean(),
            guildRepository = getBean(),
            progressionRepository = getBean(),
            progressionConfigService = getBean(),
            historyRepository = getBean(),
            adminOverrideService = getBean(),
            guildRewards = getBean(),
            invalidateClaimPermissionCache = { playerId -> invalidatePlayerClaims(claimsEnabled, playerId) },
            guildTeamProvider = { optionalGuildBean<net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService>("guildTeamService") },
            guildWaypointProvider = { optionalGuildBean<net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService>("guildWaypointService") },
            guildNotificationProvider = { optionalGuildBean<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>("guildNotificationService") }
        )
    }
    guildSingleton<RankService> {
        RankServiceBukkit(
            rankRepository = getBean(),
            memberRepository = getBean(),
            guildRepository = getBean(),
            memberService = getBean(),
            rankClaimPermissionProfiles = getBean(),
            invalidateClaimPermissionCacheForPlayer = { playerId -> invalidatePlayerClaims(claimsEnabled, playerId) },
            invalidateClaimPermissionCacheForGuild = { guildId -> invalidateGuildClaims(claimsEnabled, guildId) },
            adminOverrideService = getBean()
        )
    }
    guildSingleton<GuildService> {
        GuildServiceBukkit(
            guildRepository = getBean(),
            rankRepository = getBean(),
            memberRepository = getBean(),
            rankService = getBean(),
            memberService = getBean(),
            nexoEmojiService = getBean(),
            vaultService = getBean(),
            hologramService = getBean(),
            relationRepository = getBean(),
            historyRepository = getBean(),
            adminOverrideService = getBean(),
            homeActivationService = getBean(),
            themeAccess = getBean(),
            homeAccess = getBean(),
            configServiceProvider = { getBean<ConfigService>() },
            rewardServiceProvider = { getBean<net.lumalyte.lg.application.services.GuildRewardService>() },
            progressionServiceProvider = { getBean<net.lumalyte.lg.application.services.ProgressionService>() },
            onBannermanBannerChanged = { guildId ->
                getBean<BannermanListeners>().onGuildBannerChanged(guildId)
            }
        )
    }
    guildSingleton<RelationService> { RelationServiceBukkit(getBean(), getBean(), getBean()) }
    guildSingleton<LfgService> {
        LfgServiceBukkit(
            guildRepository = getBean(),
            guildService = getBean(),
            memberService = getBean(),
            physicalCurrencyService = getBean(),
            configService = getBean(),
            vaultService = getBean(),
            bankService = getBean(),
            rankService = getBean(),
            recruitment = getBean(),
            recruitmentEligibility = getBean()
        )
    }

    // Both simple services are now constructor-injected in either DI graph.
    guildSingleton<GuildBannerService> { GuildBannerServiceBukkit(getBean(), getBean()) }
    guildSingleton<AuditService> { AuditServiceBukkit(getBean()) }

    guildSingleton<BannermanRenderService> { BannermanRenderService(getBean<LumaGuilds>()) }
    guildSingleton<BannermanListeners> {
        BannermanListeners(getBean<LumaGuilds>(), getBean(), getBean(), getBean())
    }

    guildSingleton<StrikeRepository> { StrikeRepositorySQLite(guildStorage()) }
    guildSingleton<PenaltyRepository> { PenaltyRepositorySQLite(guildStorage()) }
    // Preserve the one-per-graph strikes configuration snapshot.
    guildSingleton<StrikesConfig> { getBean<ConfigService>().loadConfig().strikes }
    guildSingleton<StrikeService> {
        StrikeService(repository = getBean(), configProvider = { getBean<StrikesConfig>() })
    }
    guildSingleton<PenaltyService> {
        PenaltyService(
            penaltyRepository = getBean(),
            progressionService = getBean(),
            guildService = getBean(),
            configProvider = { getBean<StrikesConfig>() }
        )
    }

    // Only present when claims are enabled. Resolver uses RankService and
    // MemberService; callbacks above fetch it lazily after construction.
    if (claimsEnabled) {
        guildSingleton<GuildRolePermissionResolver> {
            GuildRolePermissionResolverBukkit(
                getBean(), getBean(), getBean(), getBean(), getBean(), getBean(),
                taskScope = requireNotNull(scope) { "Nexus coroutine scope unavailable" },
                cleanupDispatcher = requireNotNull(dispatchers).virtualThread,
            )
        }
    }
}

/** Optional plugins register their beans only after their own availability checks. */
private inline fun <reified T : Any> NexusContext.optionalGuildBean(name: String): T? =
    if (containsBean(name)) getBean(name) as T else null

private fun NexusContext.invalidatePlayerClaims(claimsEnabled: Boolean, playerId: UUID) {
    if (claimsEnabled) getBean<GuildRolePermissionResolver>().invalidatePlayerCache(playerId)
}

private fun NexusContext.invalidateGuildClaims(claimsEnabled: Boolean, guildId: UUID) {
    if (claimsEnabled) getBean<GuildRolePermissionResolver>().invalidateGuildCache(guildId)
}

@Suppress("UNCHECKED_CAST")
private fun NexusContext.guildStorage(): Storage<Database> = getBean<Storage<*>>() as Storage<Database>

private inline fun <reified T : Any> NexusContext.guildSingleton(noinline factory: () -> T) {
    registerBean(guildName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun guildName(type: KClass<*>): String =
    requireNotNull(type.simpleName).replaceFirstChar { it.lowercase() }
