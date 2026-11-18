package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-198: the simplified guild menu, grouped by row, with no separate Members button. */
class GuildDashboardLayoutContractTest {
    private val source = Paths.get("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildDashboard.kt")
        .toAbsolutePath().normalize().toFile().readText()

    @Test
    fun `dashboard groups sections by row around the guild banner`() {
        val expected = mapOf(
            "lg_nav_info" to (2 to 0), "lg_nav_progression" to (3 to 0), "lg_nav_quests" to (5 to 0), "lg_nav_statistics" to (6 to 0),
            "lg_nav_ranks" to (2 to 1), "lg_nav_economy" to (3 to 1), "lg_nav_settings" to (4 to 1), "lg_nav_stalls" to (5 to 1),
            "lg_members" to (6 to 1),
            "lg_nav_diplomacy" to (3 to 2), "lg_nav_warfare" to (5 to 2), "lg_nav_actions" to (8 to 2),
        )
        expected.forEach { (id, at) ->
            assertTrue(source.contains("addNavButton(pane, ${at.first}, ${at.second}, \"$id\""), "$id should sit at $at")
        }
        assertTrue(source.contains("addGuildInfoDisplay(pane, 4, 0)"))
        assertFalse(source.contains("\"lg_nav_members\""), "Members opens the same page as Information, so it is gone")
        // Real guild identity: the stored banner via the shared resolver (deserialize + white fallback).
        assertTrue(source.contains("GuildBannerItemResolver.resolveForDisplay(guild)"))
        assertTrue(source.contains("bankService.getBalance(guild.id)"))
        assertTrue(!source.contains("\"balance\" to guild.bankBalance"))
    }
}
