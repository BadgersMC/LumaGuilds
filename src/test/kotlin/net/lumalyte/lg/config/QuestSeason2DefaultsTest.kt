package net.lumalyte.lg.config

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestSeason2DefaultsTest {
    @Test
    fun `season two ships six weekly quests`() {
        val defaults = QuestSystemConfig()
        assertEquals(6, defaults.questCount)
        assertEquals(5_000, defaults.leaderboardWinnerXp)

        val shipped = Paths.get("src/main/resources/progression.yml").toFile().readText()
        assertTrue(shipped.contains("quest_count: 6"))
        assertTrue(shipped.contains("leaderboard_winner_xp: 5000"))
    }
}