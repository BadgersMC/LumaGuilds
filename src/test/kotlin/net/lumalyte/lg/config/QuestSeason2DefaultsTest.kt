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
        assertEquals(25_000, defaults.fullSetBonusXp)
        assertEquals(25_000, defaults.leaderboardWinnerXp)
        assertEquals(5_000, defaults.rewardXp.common)
        assertEquals(15_000, defaults.rewardXp.challenging)
        assertEquals(30_000, defaults.rewardXp.headline)
        assertEquals(50_000, defaults.rewardXp.conditioned)

        val shipped = Paths.get("src/main/resources/progression.yml").toFile().readText()
        assertTrue(shipped.contains("quest_count: 6"))
        assertTrue(shipped.contains("full_set_bonus_xp: 25000"))
        assertTrue(shipped.contains("leaderboard_winner_xp: 25000"))
    }
}