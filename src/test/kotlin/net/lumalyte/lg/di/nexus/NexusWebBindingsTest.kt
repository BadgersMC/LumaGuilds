package net.lumalyte.lg.di.nexus

import io.mockk.confirmVerified
import kotlinx.coroutines.cancel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.WebApiConfig
import net.lumalyte.lg.infrastructure.listeners.SpawnBannerListener
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.GuildLeaderboardProjectionService
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit
import net.lumalyte.lg.infrastructure.web.WebApiServer
import net.lumalyte.lg.infrastructure.web.handlers.GuildLeaderboardHandler
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class NexusWebBindingsTest {

    private val expectedNames = setOf(
        "guildLeaderboardProjectionService",
        "guildListService",
        "guildDiscordProfileService",
        "spawnBannerServiceBukkit",
        "spawnBannerListener",
        "guildLeaderboardHandler",
        "webApiServer",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    private fun privateField(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    @Test
    fun `registers the web and directory tranche by contract name`() = withContext { context ->
        val before = context.getBeanNames().toSet()
        context.registerWebBindings()
        val added = context.getBeanNames().toSet() - before

        assertEquals(7, expectedNames.size)
        assertEquals(expectedNames, added)
    }

    @Test
    fun `leaves other-tranche bindings alone`() = withContext { context ->
        context.registerWebBindings()
        val names = context.getBeanNames()

        listOf(
            // Nexus provides an Executor directly rather than a legacy named pool.
            "virtualThreadExecutor",
            // Owned by NexusQuestBindings / NexusProgressionBindings / NexusGuildCoreBindings / claims.
            "leaderboardService", "leaderboardRepository", "spawnBannerRepository", "guildListRepository",
            "killRepository", "seasonalEloCoordinator", "rewardOwnershipRepositorySQL",
            "guildBannerService", "guildService", "claimRepository",
        ).forEach { assertFalse(it in names, "unexpected bean $it") }
    }

    @Test
    fun `registration is lazy and touches no storage, configuration or plugin`() = withContext { context ->
        val storage = mockk<Storage<*>>()
        val config = mockk<ConfigService>()
        val plugin = mockk<LumaGuilds>()
        val claims = mockk<ClaimRepository>()
        context.registerBean("storage", Storage::class, storage)
        context.registerBean("configService", ConfigService::class, config)
        context.registerBean("plugin", LumaGuilds::class, plugin)
        context.registerBean("claimRepository", ClaimRepository::class, claims)

        context.registerWebBindings()

        // Strict mocks: config snapshots, schema init or plugin/scheduler access would be recorded.
        confirmVerified(storage, config, plugin, claims)
    }

    /** Every external dependency of the tranche, as inert mocks; no live Bukkit or database. */
    private fun registerDependencies(context: NexusContext, config: ConfigService, plugin: LumaGuilds) {
        context.registerBean("plugin", LumaGuilds::class, plugin)
        context.registerBean("configService", ConfigService::class, config)
        context.registerBean("langService", LangService::class, mockk<LangService>())
        context.registerBean("guildRepository", GuildRepository::class, mockk<GuildRepository>())
        context.registerBean("memberRepository", MemberRepository::class, mockk<MemberRepository>())
        context.registerBean("guildGoldRepository", GuildGoldRepository::class, mockk<GuildGoldRepository>())
        context.registerBean("killRepository", KillRepository::class, mockk<KillRepository>())
        context.registerBean("leaderboardRepository", LeaderboardRepository::class, mockk<LeaderboardRepository>())
        context.registerBean("progressionRepository", ProgressionRepository::class, mockk<ProgressionRepository>())
        context.registerBean("spawnBannerRepository", SpawnBannerRepository::class, mockk<SpawnBannerRepository>())
        context.registerBean(
            "rewardOwnershipRepository", RewardOwnershipRepository::class, mockk<RewardOwnershipRepository>(),
        )
        context.registerBean("guildService", GuildService::class, mockk<GuildService>())
        context.registerBean("memberService", MemberService::class, mockk<MemberService>())
        context.registerBean("rankService", RankService::class, mockk<RankService>())
        context.registerBean("relationService", RelationService::class, mockk<RelationService>())
        context.registerBean("guildPrestigeService", GuildPrestigeService::class, mockk<GuildPrestigeService>())
        context.registerBean("guildBannerService", GuildBannerService::class, mockk<GuildBannerService>())
        context.registerBean("bankService", BankService::class, mockk<BankService>())
        context.registerBean("leaderboardService", LeaderboardService::class, mockk<LeaderboardService>())
        context.registerBean("seasonalEloCoordinator", SeasonalEloCoordinator::class, mockk<SeasonalEloCoordinator>())
    }

    @Test
    fun `projection service receives the claim repository when claims are enabled`() = withContext { context ->
        val claims = mockk<ClaimRepository>()
        registerDependencies(context, mockk(), mockk())
        context.registerBean("claimRepository", ClaimRepository::class, claims)
        context.registerWebBindings()

        val service = context.getBean<GuildLeaderboardProjectionService>()
        assertSame(claims, privateField(service, "claimRepository"))
        assertSame(service, context.getBean<GuildLeaderboardProjectionService>())
    }

    @Test
    fun `projection service resolves with a null claim repository when claims are disabled`() = withContext { context ->
        registerDependencies(context, mockk(), mockk())
        context.registerWebBindings()

        val service = context.getBean<GuildLeaderboardProjectionService>()
        assertNull(privateField(service, "claimRepository"))
        assertSame(service, context.getBean<GuildLeaderboardProjectionService>())
    }

    @Test
    fun `web handler and server snapshot web config once at first resolve`() = withContext { context ->
        val webConfig = WebApiConfig()
        val mainConfig = mockk<MainConfig>()
        every { mainConfig.webApi } returns webConfig
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns mainConfig
        registerDependencies(context, config, mockk())
        context.registerWebBindings()

        val server = context.getBean<WebApiServer>()
        val handler = context.getBean<GuildLeaderboardHandler>()
        assertSame(server, context.getBean<WebApiServer>())
        assertSame(handler, context.getBean<GuildLeaderboardHandler>())
        assertSame(handler, privateField(server, "guildLeaderboardHandler"))
        assertSame(webConfig, privateField(server, "config"))
        assertSame(webConfig, privateField(handler, "config"))

        // One snapshot per singleton (handler + server), exactly as the Koin graph; never per request.
        verify(exactly = 2) { config.loadConfig() }
    }

    @Test
    fun `guild directory borrows the Nexus virtual-thread executor and rejects work after shutdown`() {
        val context = NexusContext.create(classLoader = javaClass.classLoader)
        try {
            context.registerBean("guildListRepository", GuildListRepository::class, mockk<GuildListRepository>())
            context.registerBean("guildRepository", GuildRepository::class, mockk<GuildRepository>())
            context.registerBean("configService", ConfigService::class, mockk<ConfigService>())
            context.registerBean(
                "progressionConfigService", ProgressionConfigService::class, mockk<ProgressionConfigService>(),
            )
            context.registerWebBindings()

            val directory = context.getBean<GuildListService>()
            assertSame(directory, context.getBean<GuildListService>())
            val executor = privateField(directory, "asyncExecutor") as Executor
            assertTrue(CompletableFuture.supplyAsync({ Thread.currentThread().isVirtual }, executor)
                .get(5, TimeUnit.SECONDS))

            requireNotNull(context.scope).cancel()
            assertFailsWith<RejectedExecutionException> { executor.execute {} }
        } finally {
            context.close()
        }
    }

    @Test
    fun `spawn banner service, listener and discord profile resolve as singletons without Bukkit`() =
        withContext { context ->
            registerDependencies(context, mockk(), mockk())
            context.registerWebBindings()

            val banners = context.getBean<SpawnBannerServiceBukkit>()
            assertSame(banners, context.getBean<SpawnBannerServiceBukkit>())
            val listener = context.getBean<SpawnBannerListener>()
            assertSame(listener, context.getBean<SpawnBannerListener>())
            assertSame(banners, privateField(listener, "service"))

            // The Bukkit name resolver is a callback; construction must not invoke it.
            val profiles = context.getBean<GuildDiscordProfileService>()
            assertSame(profiles, context.getBean<GuildDiscordProfileService>())
        }
}
