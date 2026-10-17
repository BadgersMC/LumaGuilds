package net.lumalyte.lg.infrastructure.placeholders

import io.mockk.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class WeeklyQuestDiscoveryPlaceholderTest {
    private val quests = mockk<QuestService>()
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val guildId = UUID.randomUUID()
    private val quest = QuestDefinition("stone", action = QuestAction.MINE_BLOCKS,
        target = QuestTarget("minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, 100),
        targetCount = 100, tier = QuestRewardTier.COMMON)
    private val week = WeeklyQuestSet("first", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600), listOf(quest))

    @BeforeEach fun setup() {
        stopKoin()
        startKoin { modules(module { single<QuestService> { quests }; single<GuildService> { guilds }; single<MemberService> { members } }) }
        every { quests.activeQuestSet() } returns week
        every { quests.fullSetBonusExperience } returns 5000
        every { quests.leaderboardFor(quest.id, 100) } returns listOf(GuildQuestProgress(week.weekId, quest.id, guildId, currentCount = 175))
        every { guilds.getGuild(guildId) } returns mockk<Guild> { every { name } returns "Everlights" }
    }
    @AfterEach fun teardown() { stopKoin() }

    @Test fun `playerless spawn placeholders display names and uncapped scores with cached leaderboard`() {
        val expansion = LumaGuildsExpansion()
        assertEquals("true", expansion.onPlaceholderRequest(null, "guild_weekly_quests_available"))
        assertEquals("Mine Stone", expansion.onPlaceholderRequest(null, "guild_weekly_quest_1_name"))
        assertEquals("Everlights", expansion.onPlaceholderRequest(null, "guild_weekly_quest_1_top_1_guild"))
        assertEquals("175", expansion.onPlaceholderRequest(null, "guild_weekly_quest_1_top_1_score"))
        verify(exactly = 1) { quests.leaderboardFor(quest.id, 100) }
    }

    @Test fun `missing and out of range positions return empty`() {
        val expansion = LumaGuildsExpansion()
        for (field in listOf("top_0_score", "top_101_score", "top_2_guild")) {
            assertEquals("", expansion.onPlaceholderRequest(null, "guild_weekly_quest_1_$field"))
        }
        assertEquals("", expansion.onPlaceholderRequest(null, "guild_weekly_quest_0_name"))
    }

    @Test fun `new week bypasses old leaderboard cache`() {
        val expansion = LumaGuildsExpansion()
        assertEquals("175", expansion.onPlaceholderRequest(null, "guild_weekly_quest_1_top_1_score"))
        every { quests.activeQuestSet() } returns week.copy(weekId = "second")
        every { quests.leaderboardFor(quest.id, 100) } returns emptyList()
        assertEquals("", expansion.onPlaceholderRequest(null, "guild_weekly_quest_1_top_1_score"))
        verify(exactly = 2) { quests.leaderboardFor(quest.id, 100) }
    }

    @Test fun `viewer rank and score follow weekly rollover and rank polling is cached`() {
        val playerId = UUID.randomUUID()
        val player = mockk<org.bukkit.entity.Player> { every { uniqueId } returns playerId }
        every { members.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { quests.guildProgress(guildId) } returns listOf(GuildQuestProgress("first", quest.id, guildId, currentCount = 175))
        every { quests.isWeeklyBonusAwarded(guildId) } returns false
        every { quests.rankFor(guildId, quest.id) } returns 1
        val expansion = LumaGuildsExpansion()
        assertEquals("175", expansion.onPlaceholderRequest(player, "guild_weekly_quest_1_score"))
        repeat(2) { assertEquals("1", expansion.onPlaceholderRequest(player, "guild_weekly_quest_1_rank")) }
        verify(exactly = 1) { quests.rankFor(guildId, quest.id) }
        every { quests.activeQuestSet() } returns week.copy(weekId = "second")
        every { quests.guildProgress(guildId) } returns emptyList()
        every { quests.rankFor(guildId, quest.id) } returns null
        assertEquals("0", expansion.onPlaceholderRequest(player, "guild_weekly_quest_1_score"))
        assertEquals("", expansion.onPlaceholderRequest(player, "guild_weekly_quest_1_rank"))
    }
}
