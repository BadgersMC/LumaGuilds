package net.lumalyte.lg.di.nexus

import co.aikar.idb.Database
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class NexusProgressionBindingsTest {

    private val expectedNames = setOf(
        // Rewards, chronicle and prestige
        "guildAchievementRuntime", "guildActionCoordinator", "rewardCatalog",
        "chronicleGoalRepository", "guildChronicleService", "rewardPurchaseRepositorySQL",
        "guildRewardPurchaseAccess", "guildRewardPurchaseService", "rewardOwnershipRepositorySQL",
        "prestigeRepository", "guildPrestigeAnnouncement", "guildPrestigeService",
        "rewardStateRepositorySQL", "guildRewardService",
        // Repositories
        "killRepository", "progressionRepository", "experienceAwardRepositorySQL",
        "guildShopXpRepository", "bankProgressionRepository", "bankDepositXpRepository",
        "bankDepositXpQuotes", "bankDepositXpDeliveryService", "leaderboardRepository",
        "guildListRepository", "spawnBannerRepository", "questRepository", "blockProvenanceRepository",
        // Services
        "killService", "modeService", "combatService", "playtimeActivityService",
        "permanentExperienceService", "chapterTwoGuildAwardService", "seasonalEloCoordinator",
        "progressionConfigService", "progressionService",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    /** A storage double whose schema DDL calls are no-ops; no live database. */
    private fun stubStorage(): Storage<Database> {
        val database = mockk<Database>(relaxed = true)
        val storage = mockk<Storage<Database>>()
        every { storage.connection } returns database
        every { storage.dialect } returns SqlDialect.SQLITE
        return storage
    }

    @Test
    fun `registers the first progression tranche by contract name`() = withContext { context ->
        val before = context.getBeanNames().toSet()
        context.registerProgressionBindings()
        val added = context.getBeanNames().toSet() - before

        assertEquals(36, expectedNames.size)
        assertEquals(expectedNames, added)
    }

    @Test
    fun `collapses redundant interface aliases and omits the executor-backed provenance queue`() =
        withContext { context ->
            context.registerProgressionBindings()
            val names = context.getBeanNames()

            assertFalse("rewardOwnershipRepository" in names)
            assertFalse("experienceAwardRepository" in names)
            // Requires an Executor bridge; must not allocate a stand-alone ExecutorService.
            assertFalse("blockProvenanceOperationQueue" in names)
            // Deferred tranche (war / quest / notifications / web).
            assertFalse("warService" in names)
            assertFalse("questService" in names)
            assertFalse("leaderboardService" in names)
        }

    @Test
    fun `registration is lazy and touches neither storage nor configuration`() = withContext { context ->
        val storage = mockk<Storage<*>>()
        val config = mockk<ConfigService>()
        context.registerBean("storage", Storage::class, storage)
        context.registerBean("configService", ConfigService::class, config)

        context.registerProgressionBindings()

        // Strict mocks: any schema init or config read during registration would be recorded.
        confirmVerified(storage, config)
    }

    @Test
    fun `dependency-free progression beans are singletons`() = withContext { context ->
        context.registerProgressionBindings()

        assertSame(context.getBean<GuildActionCoordinator>(), context.getBean<GuildActionCoordinator>())
        assertSame(context.getBean<RewardCatalog>(), context.getBean<RewardCatalog>())
        assertSame(context.getBean<PlaytimeActivityService>(), context.getBean<PlaytimeActivityService>())
    }

    @Test
    fun `experience award repository is one instance under concrete and interface lookups`() =
        withContext { context ->
            context.registerBean("storage", Storage::class, stubStorage())
            // No ConfigService registered: the XP curve provider must stay deferred.
            context.registerProgressionBindings()

            val concrete = context.getBean<ExperienceAwardRepositorySQL>()
            val contract = context.getBean<ExperienceAwardRepository>()

            assertSame(concrete as Any, contract as Any)
            assertSame(concrete, context.getBean<ExperienceAwardRepositorySQL>())
        }

    @Test
    fun `chapter two award service builds without eagerly resolving ProgressionService`() =
        withContext { context ->
            context.registerBean("storage", Storage::class, stubStorage())
            context.registerBean("configService", ConfigService::class, mockk<ConfigService>())
            context.registerBean(
                "membershipHistoryRepository", MembershipHistoryRepository::class,
                mockk<MembershipHistoryRepository>(),
            )
            context.registerProgressionBindings()

            // ProgressionService needs GuildRepository/LangService/plugin, none registered here.
            // Construction succeeding proves the callback defers that lookup (cycle preserved).
            val award = context.getBean<ChapterTwoGuildAwardService>()
            assertSame(award, context.getBean<ChapterTwoGuildAwardService>())
        }
}
