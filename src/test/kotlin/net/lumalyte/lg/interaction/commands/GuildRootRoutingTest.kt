package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BukkitCommandManager
import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID

class GuildRootRoutingTest {
    @AfterEach fun cleanup() { stopKoin(); unmockkAll(); MockBukkit.unmock() }

    @Test fun rootsUseMenuAndRespectPermission() {
        stopKoin()
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        val guilds = mockk<GuildService>()
        val ranks = mockk<RankService>()
        val overrides = mockk<AdminOverrideService>()
        val factory = mockk<MenuFactory>()
        val lang = mockk<LangService>(relaxed = true)
        val menu = mockk<Menu>(relaxed = true)
        val guild = mockk<Guild>()
        val rank = mockk<Rank>()
        val guildId = UUID.randomUUID()
        every { guild.id } returns guildId
        every { rank.id } returns UUID.randomUUID()
        every { guilds.getPlayerGuilds(player.uniqueId) } returns setOf(guild)
        every { ranks.getPlayerRank(player.uniqueId, guildId) } returns rank
        every { ranks.getHighestRank(guildId) } returns rank
        every { overrides.hasOverride(player.uniqueId) } returns false
        every { factory.createGuildControlPanelMenu(any(), player, guild) } returns menu
        startKoin { modules(module { single { guilds }; single { ranks }; single { overrides }; single { factory }; single { lang } }) }
        player.addAttachment(plugin, "lumaguilds.guild.menu", true)
        BukkitCommandManager(plugin).registerCommand(GuildCommand())
        for (command in listOf("guild", "g", "guild menu")) {
            clearMocks(menu, answers = false)
            server.dispatchCommand(player, command)
            verify(exactly = 1) { menu.open() }
        }
        // A regular member receives the public guild information menu instead of
        // the management panel; the explicit management command remains restricted.
        val highest = mockk<Rank>()
        val info = mockk<Menu>(relaxed = true)
        every { rank.id } returns UUID.randomUUID()
        every { highest.id } returns UUID.randomUUID()
        every { rank.permissions } returns emptySet()
        every { ranks.getHighestRank(guildId) } returns highest
        every { overrides.hasOverride(player.uniqueId) } returns false
        every { factory.createGuildInfoMenu(any(), player, guild) } returns info
        for (command in listOf("guild", "g")) {
            clearMocks(info, menu, answers = false)
            server.dispatchCommand(player, command)
            verify(exactly = 1) { info.open() }
            verify(exactly = 0) { menu.open() }
        }
        clearMocks(info, menu, answers = false)
        server.dispatchCommand(player, "guild menu")
        verify(exactly = 0) { info.open() }
        verify(exactly = 0) { menu.open() }
        // Missing rank records must not make null == null look like ownership.
        every { ranks.getPlayerRank(player.uniqueId, guildId) } returns null
        every { ranks.getHighestRank(guildId) } returns null
        clearMocks(info, menu, answers = false)
        server.dispatchCommand(player, "guild")
        verify(exactly = 1) { info.open() }
        verify(exactly = 0) { menu.open() }
        clearMocks(info, menu, answers = false)
        server.dispatchCommand(player, "guild menu")
        verify(exactly = 0) { menu.open() }
        // Missing either rank record must also fall back unless the member has
        // an actual management permission.
        every { ranks.getPlayerRank(player.uniqueId, guildId) } returns rank
        clearMocks(info, menu, answers = false)
        server.dispatchCommand(player, "guild")
        verify(exactly = 1) { info.open() }
        verify(exactly = 0) { menu.open() }
        every { ranks.getPlayerRank(player.uniqueId, guildId) } returns null
        every { ranks.getHighestRank(guildId) } returns highest
        clearMocks(info, menu, answers = false)
        server.dispatchCommand(player, "guild")
        verify(exactly = 1) { info.open() }
        verify(exactly = 0) { menu.open() }
        val browser = mockk<Menu>(relaxed = true)
        every { guilds.getPlayerGuilds(player.uniqueId) } returns emptySet()
        every { factory.createLfgBrowserMenu(any(), player) } returns browser
        for (command in listOf("guild", "g")) {
            clearMocks(browser, answers = false)
            server.dispatchCommand(player, command)
            verify(exactly = 1) { browser.open() }
        }
        player.addAttachment(plugin, "lumaguilds.guild.menu", false)
        clearMocks(menu, browser, answers = false)
        server.dispatchCommand(player, "guild")
        verify(exactly = 0) { menu.open() }
        verify(exactly = 0) { browser.open() }
    }
}
