package net.lumalyte.lg.interaction.menus.guild

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class GuildBankSeason2FollowupContractTest {
    private val root = Paths.get(System.getProperty("user.dir"))

    @Test
    fun `history helper labels are localized`() {
        val locale = YamlConfiguration.loadConfiguration(root.resolve("src/main/resources/lang/en_US.yml").toFile())
        listOf(
            "menu.bank.close",
            "menu.bank.history.filter.type",
            "menu.bank.history.filter.member",
            "menu.bank.history.filter.date",
            "menu.bank.history.filter.search",
            "menu.bank.history.filter.clear",
            "menu.bank.stats.budget.status",
        ).forEach { assertNotNull(locale.getString(it), "Missing $it") }
    }

    @Test
    fun `java automation menu has no fake coming soon click handlers`() {
        val source = root.resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildBankAutomationMenu.kt").toFile().readText()
        assertFalse(source.contains("TODO: Open reward setup menu"))
        assertFalse(source.contains("TODO: Open alert configuration menu"))
        assertFalse(source.contains("TODO: Open recurring payment setup"))
        assertFalse(source.contains("TODO: Show detailed automation list"))
        assertFalse(source.contains("_coming_soon"))
    }
}
