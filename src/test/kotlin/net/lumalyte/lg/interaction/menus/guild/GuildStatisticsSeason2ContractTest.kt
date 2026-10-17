package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildStatisticsSeason2ContractTest {
    private val source = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildStatisticsMenu.kt"
    ).toFile().readText()

    @Test
    fun `statistics menu does not expose obsolete or fake controls`() {
        assertFalse(source.contains("addExportStatsButton(pane"))
        assertFalse(source.contains("private fun addExportStatsButton"))
        assertFalse(source.contains("addTrendAnalysisButton(pane, 0, 3)"))
    }

    @Test
    fun `statistics menu retains supported analytical surfaces`() {
        listOf(
            "addTopKillersButton",
            "addTopContributorsButton",
            "addPeriodStatsButton",
            "addRivalryStatsButton",
            "addAchievementsButton",
            "addComparisonButton",
        ).forEach { assertTrue(source.contains(it), "Missing retained statistics surface $it") }
    }

    @Test
    fun `statistics menu is wired for the season two icon family`() {
        listOf(
            "lg_stats_kills",
            "lg_stats_wars",
            "lg_stats_members",
            "lg_stats_performance",
            "lg_stats_top_killers",
            "lg_stats_top_contributors",
            "lg_stats_top_inviters",
            "lg_stats_kd_analysis",
            "lg_stats_recent",
            "lg_stats_periodic",
            "lg_stats_rivalry",
            "lg_stats_achievements",
            "lg_stats_comparison",
            "lg_stats_refresh",
        ).forEach { assertTrue(source.contains(it), "Missing Season 2 icon hook $it") }
    }
}