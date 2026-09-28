package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockGuildQuestsWiringTest {
    private val source = File(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildQuestsMenu.kt"
    ).readText()

    @Test
    fun `Bedrock quests consume the shared quest read and leaderboard model`() {
        listOf(
            "questService.activeQuestSet()",
            "questService.guildProgress(guild.id)",
            "questService.timeRemaining()",
            "questService.isWeeklyBonusAwarded(guild.id)",
            "questService.rankFor(guild.id, quest.id)",
            "BedrockGuildQuestLeaderboardMenu(",
            "QuestDisplayFormatter.name(quest)",
            "QuestDisplayFormatter.description(quest)"
        ).forEach { expected ->
            assertTrue(source.contains(expected), "missing shared quest behavior: $expected")
        }
    }

    @Test
    fun `Bedrock quest mutations return to the server thread and never generate quests`() {
        assertTrue(source.contains("Bukkit.getScheduler().runTask"))
        assertFalse(source.contains("QuestGenerator"))
        assertFalse(source.contains("resetWeeklyQuests"))
        assertFalse(source.contains("saveActiveQuestSet"))
    }

    @Test
    fun `Bedrock quest form exposes six item page and completion states`() {
        assertTrue(source.contains("private const val PAGE_SIZE = 6"))
        assertTrue(source.contains("menu.quests.item.quest.claimed"))
        assertTrue(source.contains("menu.quests.item.quest.completed"))
        assertTrue(source.contains("menu.quests.item.quest.in_progress"))
        assertTrue(source.contains("menu.quests.item.quest.view_leaderboard"))
        assertTrue(source.contains("bedrock.quests.item_rewards"))
        assertFalse(source.contains("questService.claimQuest("))
    }
}
