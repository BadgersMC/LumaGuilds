package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockGuildStatisticsTruthfulnessContractTest {
    private val source = File(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildStatisticsMenu.kt"
    ).readText()

    @Test
    fun `Bedrock statistics contain no fabricated believable values`() {
        listOf(
            "\"0/800\"",
            "maxOfOrNull { it.joinedAt }",
            "bedrock.statistics.value.active",
            "Placeholder values for territory statistics",
            "val totalClaims = 0",
            "val controlledArea = 0",
            "val powerLevel = 1"
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "fabricated or misleading statistics remain: $forbidden")
        }
    }

    @Test
    fun `Bedrock statistics consume the authoritative statistics services`() {
        listOf(
            "KillService",
            "WarService",
            "LeaderboardService",
            "GuildService",
            "killService.getGuildKillStats(guild.id)",
            "warService.getWarHistory(guild.id",
            "killService.getTopKillers",
            "bankService.getMemberContributions(guild.id)",
            "leaderboardService.getEntityEntry",
            "killService.getKillsBetweenGuilds"
        ).forEach { expected ->
            assertTrue(source.contains(expected), "missing authoritative statistics behavior: $expected")
        }
    }

    @Test
    fun `Bedrock statistics expose explicit empty or unavailable states and no export`() {
        listOf(
            "menu.statistics.common.no_kill_data",
            "menu.statistics.common.no_contribution_data",
            "menu.statistics.common.no_period_data",
            "menu.statistics.common.no_rivalry_data",
            "menu.statistics.common.unavailable"
        ).forEach { expected ->
            assertTrue(source.contains(expected), "missing explicit data state: $expected")
        }
        assertFalse(source.contains("CSV", ignoreCase = true))
        assertFalse(source.contains("export", ignoreCase = true))
    }
}
