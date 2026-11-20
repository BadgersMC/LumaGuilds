package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildStatisticsDataStateContractTest {
    private val source = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildStatisticsMenu.kt"
    ).toFile().readText()

    @Test
    fun validEmptyDataIsDistinguishedFromUnavailableData() {
        // REQ-201: Combat says when no kills are logged yet; Rivalries says when there are none.
        assertTrue(source.contains("menu.statistics.item.combat.lore.empty"))
        assertTrue(source.contains("menu.statistics.common.no_rivalry_data"))
        assertFalse(source.contains("private fun addTrendAnalysisButton"))
        assertFalse(source.contains("private fun openTrendAnalysis"))
        assertFalse(source.contains("private fun exportGuildStatistics"))
    }
}
