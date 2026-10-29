package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildQuestsSeason2ContractTest {
    private val source =
        Paths.get(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildQuestsMenu.kt",
        ).toFile().readText()
    private val locale = Paths.get("src/main/resources/lang/en_US.yml").toFile().readText()

    @Test
    fun `weekly quest menu uses a centered six quest grid and human formatter`() {
        assertTrue(source.contains("private val slots = listOf(20, 22, 24, 29, 31, 33)"))
        assertTrue(source.contains("QuestDisplayFormatter.name(quest)"))
        assertTrue(source.contains("QuestDisplayFormatter.description(quest)"))
    }

    @Test
    fun `weekly quest items use action artwork and open read only details`() {
        assertTrue(source.contains("QuestIconProvider.itemFor(quest)"))
        assertTrue(source.contains("GuildQuestLeaderboardMenu("))
        assertTrue(source.contains("GuildQuestDetailMenu("))
        assertTrue(source.contains("dialog_v2.view_quest"))
        assertFalse(source.contains("questService.claimQuest("))
    }

    @Test
    fun `weekly quest locale uses the objective placeholder contract`() {
        assertTrue(locale.contains("name: '<gold><objective>'"))
        assertTrue(locale.contains("description: '<gray><objective>'"))
        assertTrue(source.contains("\"objective\" to QuestDisplayFormatter.name(quest)"))
        assertTrue(source.contains("\"objective\" to QuestDisplayFormatter.description(quest)"))
    }
}
