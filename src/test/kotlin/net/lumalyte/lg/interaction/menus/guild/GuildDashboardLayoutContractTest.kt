package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertTrue

class GuildDashboardLayoutContractTest {
    private val source = Paths.get("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildDashboard.kt")
        .toAbsolutePath().normalize().toFile().readText()

    @Test
    fun `dashboard keeps ten sections statistics under economy and real guild identity`() {
        val expected = listOf(
            "lg_nav_info", "lg_nav_members", "lg_nav_ranks", "lg_nav_quests", "lg_nav_economy",
            "lg_nav_settings", "lg_nav_progression", "lg_nav_diplomacy", "lg_nav_warfare", "lg_nav_statistics",
        )
        expected.forEach { id ->
            assertTrue(source.contains("\"" + id + "\""), "Missing dashboard section " + id)
        }
        assertTrue(source.contains("""addNavButton(pane, 8, 1, "lg_nav_economy""""))
        assertTrue(source.contains("""addNavButton(pane, 8, 2, "lg_nav_statistics""""))
        assertTrue(source.contains("addGuildInfoDisplay(pane, 4, 0)"))
        assertTrue(source.contains("guild.banner?.deserializeToItemStack()?.clone()"))
        assertTrue(source.contains("bankService.getBalance(guild.id)"))
        assertTrue(!source.contains("\"balance\" to guild.bankBalance"))
    }
}
