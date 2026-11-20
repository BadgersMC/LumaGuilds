package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-201/REQ-202: the simplified 3-row statistics menu. */
class GuildStatisticsLayoutContractTest {
    private val source = Paths.get("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildStatisticsMenu.kt")
        .toAbsolutePath().normalize().toFile().readText()

    @Test
    fun `eight sections in three rows`() {
        listOf(
            "addCombatButton(pane, 2, 0)", "addWarStatsButton(pane, 3, 0)", "addRecentActivityButton(pane, 4, 0)",
            "addRivalryStatsButton(pane, 5, 0)", "addAchievementsButton(pane, 6, 0)",
            "addTopKillersButton(pane, 3, 1)", "addTopContributorsButton(pane, 4, 1)", "addTopInvitersButton(pane, 5, 1)",
            "addBackButton(pane, 4, 2)",
        ).forEach { assertTrue(source.contains(it), "missing $it") }
        assertTrue(source.contains("ChestGui(3, MenuTitleBuilder.build("))
    }

    @Test
    fun `removed sections are gone`() {
        listOf(
            "addMemberStatsButton", "addPerformanceButton", "addKillDeathRatiosButton", "addPeriodStatsButton",
            "addComparisonButton", "addRefreshStatsButton", "addKillStatsButton",
        ).forEach { assertFalse(source.contains(it), "$it should be removed") }
    }

    @Test
    fun `rivalries include guilds fought outside wars`() {
        assertTrue(source.contains("killLog.rivals(guild.id"))
    }
}
