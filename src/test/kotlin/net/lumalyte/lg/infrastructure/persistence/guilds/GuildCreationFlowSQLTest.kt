package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.services.*
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.*
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.*

/** Real payment journal, creation SQL, default ranks, member admission and reward read. */
class GuildCreationFlowSQLTest : RewardSqlTestFixture() {
    @AfterEach fun clearRuntime() { stopKoin(); unmockkAll(); MockBukkit.unmock() }

    @Test fun paidCreationEstablishesOwnerAndCommitsExactlyOnePayment() {
        stopKoin()
        val server = MockBukkit.mock()
        val player = spyk(server.addPlayer())
        every { player.saveData() } just Runs
        player.inventory.setItem(0, ItemStack(Material.RAW_GOLD, 20))
        val storage = openStorage()
        migrateProductionSchema(storage)
        val cfg = MainConfig(chapterTwoGoldCostsEnabled = true, guild = GuildConfig(createGuildCost = 10))
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns cfg
        startKoin { modules(module { single { config } }) }
        val guilds = GuildRepositorySQLite(storage) { 4321 }
        val ranks = RankRepositorySQLite(storage)
        val members = MemberRepositorySQLite(storage)
        val progression = ProgressionRepositorySQLite(storage, config)
        val rewards = GuildRewardService(RewardStateRepositorySQL(storage,
            RewardOwnershipRepositorySQL(storage, RewardCatalog.chapterTwo())), RewardCatalog.chapterTwo()) {
            RewardReadSettings(true, 100000, 20)
        }
        val memberService = MemberServiceBukkit(members, ranks, guilds, progression,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), rewards)
        val rankService = RankServiceBukkit(ranks, members, guilds, memberService, mockk(relaxed = true))
        val guildService = GuildServiceBukkit(guilds, ranks, members, rankService, memberService,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val physical = BukkitPhysicalGoldAdapter({ server.getPlayer(it) }, Material.RAW_GOLD, null, 1,
            PhysicalGoldJournal(storage))
        val costs = GuildCostService({ cfg }, physical, mockk(relaxed = true))
        val result = assertIs<GuildCreationCostResult.Applied>(costs.createGuild(UUID.randomUUID(), player.uniqueId) {
            guildService.createGuild("Complete Guild", player.uniqueId, null)
        })
        val owner = assertNotNull(members.getByPlayerAndGuild(player.uniqueId, result.guild.id))
        assertEquals(0, assertNotNull(ranks.getById(owner.rankId)).priority)
        assertIs<GuildRewardRead.Available>(rewards.read(result.guild.id))
        assertEquals(4321, assertNotNull(progression.getGuildProgression(result.guild.id)).experienceForNextLevel)
        assertEquals(10L, result.cost)
        assertEquals(10, player.inventory.getItem(0)!!.amount)
        assertNull(physical.pendingReservation(player.uniqueId))
    }
}
