package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BukkitCommandManager
import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit

/** Exercise real ACF registration rather than inferring routing from annotations. */
class GuildQuestRoutingTest {
    private val guilds = mockk<GuildService>()
    private val factory = mockk<MenuFactory>()
    private val lang = mockk<LangService>(relaxed = true)
    private val menu = mockk<Menu>(relaxed = true)

    @BeforeEach fun setup() { stopKoin() }
    @AfterEach fun cleanup() { stopKoin(); unmockkAll(); MockBukkit.unmock() }

    @Test fun `both roots route short and menu quest aliases to member menu`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        player.addAttachment(plugin, "lumaguilds.guild.quests", true)
        startKoin { modules(module {
            single { guilds }; single { factory }; single { lang }
        }) }
        every { guilds.getPlayerGuilds(player.uniqueId) } returns setOf(mockk<Guild>())
        every { factory.createGuildQuestsMenu(any(), player, any()) } returns menu
        val manager = BukkitCommandManager(plugin)
        manager.registerCommand(GuildCommand())
        for (root in listOf("g", "guild")) {
            for (path in listOf("quest", "quests", "quest menu", "quests menu")) {
                clearMocks(menu, answers = false)
                server.dispatchCommand(player, "$root $path")
                verify(exactly = 1) { menu.open() }
            }
        }
    }
}
