package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals

internal class GuildHomeReturnTest : GuildParentReturnFixture() {
    @Test fun `home and ally access Back unwind to the existing homes screen even after repeated clicks`() {
        val constructors: List<(MenuNavigator) -> Menu> = listOf(
            { HomeAccessMenu(it, player, guild, "Home") },
            { AllyHomeAccessMenu(it, player, guild) },
        )
        constructors.forEach { create ->
            val navigator = MenuNavigator(player)
            val visits = mutableListOf<String>()
            val homes = mockk<GuildHomeMenu>(relaxed = true)
            every { homes.open() } answers { visits += "homes"; Unit }
            every { factory.createGuildHomeMenu(navigator, player, guild) } returns homes
            val access = traced(create(navigator), "access", visits)
            navigator.openMenu(dashboard(visits))
            navigator.openMenu(homes)
            navigator.openMenu(access)
            val buttons = paneFor(access, "addBackButton")
            repeat(2) { click(buttons, 8, 3) }
            navigator.goBack()
            assertEquals("dashboard", visits.last(), access.javaClass.simpleName)
            assertEquals(1, visits.count { it == "access" })
            verify(exactly = 0) { factory.createGuildHomeMenu(navigator, player, guild) }
        }
    }

    @Test fun `revoked home permission returns without leaving the denied editor in history`() {
        every { ranks.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME) } returns false
        assertGuardReturn { HomeAccessMenu(it, player, guild, "Home") }
        assertGuardReturn { AllyHomeAccessMenu(it, player, guild) }
    }

    @Test fun `removed home returns without reopening the missing-home editor on Back`() {
        every { ranks.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME) } returns true
        val guilds = GlobalContext.get().get<GuildService>()
        every { guilds.getHome(guild.id, "Home") } returns null
        assertGuardReturn { HomeAccessMenu(it, player, guild, "Home") }
    }

    @Test fun `direct denied entry creates one homes fallback and Back does not retry the editor`() {
        val navigator = MenuNavigator(player)
        val homes = mockk<GuildHomeMenu>(relaxed = true)
        every { ranks.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME) } returns false
        every { factory.createGuildHomeMenu(navigator, player, guild) } returns homes
        navigator.openMenu(HomeAccessMenu(navigator, player, guild, "Home"))
        navigator.goBack()
        verify(exactly = 1) { homes.open() }
        verify(exactly = 1) { factory.createGuildHomeMenu(navigator, player, guild) }
    }

    private fun assertGuardReturn(create: (MenuNavigator) -> Menu) {
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val homes = mockk<GuildHomeMenu>(relaxed = true)
        every { homes.open() } answers { visits += "homes"; Unit }
        every { factory.createGuildHomeMenu(navigator, player, guild) } returns homes
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(homes)
        navigator.openMenu(create(navigator))
        assertEquals(listOf("dashboard", "homes", "homes"), visits)
        navigator.goBack()
        assertEquals("dashboard", visits.last())
    }
}
