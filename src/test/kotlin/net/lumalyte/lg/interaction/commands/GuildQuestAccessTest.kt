package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuildQuestAccessTest {
    private val guilds = mockk<GuildService>()
    private val factory = mockk<MenuFactory>()
    private val lang = mockk<LangService>()
    private val player = mockk<Player>(relaxed = true)
    private val playerId = UUID.randomUUID()
    @BeforeEach fun setup() {
        stopKoin()
        startKoin { modules(module {
            single<GuildService> { guilds }; single<MenuFactory> { factory }; single<LangService> { lang }
        }) }
        every { player.uniqueId } returns playerId
    }
    @AfterEach fun cleanup() { stopKoin() }

    @Test fun `ordinary member opens quest menu with no management services installed`() {
        val guild = mockk<Guild>()
        val menu = mockk<Menu>(relaxed = true)
        every { guilds.getPlayerGuilds(playerId) } returns setOf(guild)
        every { factory.createGuildQuestsMenu(any(), player, guild) } returns menu
        GuildCommand().onQuests(player)
        verify(exactly = 1) { menu.open() }
        verify(exactly = 0) { factory.createGuildControlPanelMenu(any(), any(), any()) }
    }

    @Test fun `nonmember receives feedback without opening menu`() {
        every { guilds.getPlayerGuilds(playerId) } returns emptySet()
        every { lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild") } returns Component.text("No guild")
        GuildCommand().onQuests(player)
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
        verify(exactly = 0) { factory.createGuildQuestsMenu(any(), any(), any()) }
    }

    @Test fun `command exposes aliases and dedicated default true permission`() {
        val method = GuildCommand::class.java.getMethod("onQuests", Player::class.java)
        assertEquals("lumaguilds.guild.quests", method.getAnnotation(CommandPermission::class.java).value)
        assertEquals(setOf("quests", "quest", "quest menu"), method.getAnnotation(Subcommand::class.java).value.split('|').toSet())
        val yaml = YamlConfiguration().also {
            it.options().pathSeparator('/')
            it.load(File("src/main/resources/plugin.yml"))
        }
        assertEquals("true", yaml.getString("permissions/lumaguilds.guild.quests/default"))
        assertTrue(yaml.getBoolean("permissions/lumaguilds.guild.*/children/lumaguilds.guild.quests"))
    }
}

