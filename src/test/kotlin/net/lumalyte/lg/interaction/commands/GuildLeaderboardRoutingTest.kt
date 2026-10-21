package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BukkitCommandManager
import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuildLeaderboardRoutingTest {
    private val guilds = mockk<GuildService>()
    private val factory = mockk<MenuFactory>()
    private val lang = mockk<LangService>(relaxed = true)
    private val menu = mockk<Menu>(relaxed = true)

    @BeforeEach fun setup() { stopKoin() }
    @AfterEach fun cleanup() { stopKoin(); unmockkAll(); MockBukkit.unmock() }

    @Test fun bothRootsOpenRankingsWithoutManagementServices() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        player.addAttachment(plugin, "lumaguilds.guild.leaderboard", true)
        player.addAttachment(plugin, "lumaguilds.guild.menu", false)
        startKoin { modules(module { single { guilds }; single { factory }; single { lang } }) }
        every { guilds.getPlayerGuilds(player.uniqueId) } returns setOf(mockk<Guild>())
        every { factory.createGuildLeaderboardMenu(any(), player, any()) } returns menu
        BukkitCommandManager(plugin).registerCommand(GuildCommand())
        for (root in listOf("g", "guild")) {
            clearMocks(menu, answers = false)
            server.dispatchCommand(player, "$root leaderboard")
            verify(exactly = 1) { menu.open() }
        }
        verify(exactly = 0) { factory.createGuildControlPanelMenu(any(), any(), any()) }
        player.addAttachment(plugin, "lumaguilds.guild.leaderboard", false)
        clearMocks(menu, answers = false)
        server.dispatchCommand(player, "g leaderboard")
        verify(exactly = 0) { menu.open() }
    }

    @Test fun nonmemberDoesNotOpenAndPermissionDefaultsToMemberBrowsing() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        player.addAttachment(plugin, "lumaguilds.guild.leaderboard", true)
        startKoin { modules(module { single { guilds }; single { factory }; single { lang } }) }
        every { guilds.getPlayerGuilds(player.uniqueId) } returns emptySet()
        every { lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild") } returns Component.text("No guild")
        BukkitCommandManager(plugin).registerCommand(GuildCommand())
        server.dispatchCommand(player, "guild leaderboard")
        verify(exactly = 0) { factory.createGuildLeaderboardMenu(any(), any(), any()) }
        val yaml = YamlConfiguration().also { it.options().pathSeparator('/'); it.load(File("src/main/resources/plugin.yml")) }
        assertEquals("true", yaml.getString("permissions/lumaguilds.guild.leaderboard/default"))
        assertTrue(yaml.getBoolean("permissions/lumaguilds.guild.*/children/lumaguilds.guild.leaderboard"))
    }
}
