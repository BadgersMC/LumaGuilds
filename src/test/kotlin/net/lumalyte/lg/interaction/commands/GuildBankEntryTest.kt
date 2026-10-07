package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class GuildBankEntryTest {
    @AfterEach
    fun cleanup() { stopKoin() }

    @Test
    fun `bank-only member can open platform bank without management services`() {
        val player = mockk<Player>()
        val guildService = mockk<GuildService>()
        val factory = mockk<MenuFactory>()
        val menu = mockk<Menu>(relaxed = true)
        val playerId = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Bank guild", createdAt = Instant.EPOCH)
        every { player.uniqueId } returns playerId
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild)
        every { factory.createGuildBankMenu(any(), player, guild) } returns menu
        stopKoin()
        startKoin { modules(module { single { guildService }; single { factory } }) }
        GuildCommand().onBank(player)
        verify(exactly = 1) { menu.open() }
    }

    @Test
    fun `guildless player cannot open bank`() {
        val player = mockk<Player>(relaxed = true)
        val guildService = mockk<GuildService>()
        val factory = mockk<MenuFactory>(relaxed = true)
        val lang = mockk<LangService>(relaxed = true)
        every { guildService.getPlayerGuilds(player.uniqueId) } returns emptySet()
        stopKoin()
        startKoin { modules(module { single { guildService }; single { factory }; single { lang } }) }
        GuildCommand().onBank(player)
        verify(exactly = 0) { factory.createGuildBankMenu(any(), any(), any()) }
        verify(exactly = 1) { player.sendMessage(any<net.kyori.adventure.text.Component>()) }
    }

    @Test
    fun `bank shortcut uses existing menu command permission`() {
        val method = GuildCommand::class.java.declaredMethods.single { it.name == "onBank" }
        assertEquals("bank", method.getAnnotation(Subcommand::class.java).value)
        assertEquals("lumaguilds.guild.menu", method.getAnnotation(CommandPermission::class.java).value)
    }
}
