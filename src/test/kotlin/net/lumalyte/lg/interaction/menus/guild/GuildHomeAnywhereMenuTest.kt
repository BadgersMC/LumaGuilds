package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildHomeActivationService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.HomeActivationCostResult
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.GuildHomes
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildHomeMenu
import org.bukkit.Location
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.logging.Logger
import kotlin.test.assertEquals

/** Real Java/Bedrock menu handlers work without a GetClaimAtPosition registration. */
internal class GuildHomeAnywhereMenuTest : GuildRankMenuFixture() {
    private fun installPaidHomeFixture(): Pair<GuildService, GuildHomeActivationService> {
        val koin = GlobalContext.get()
        val guilds = koin.get<GuildService>()
        val config = koin.get<ConfigService>()
        every { config.loadConfig() } returns MainConfig(
            claimsEnabled = true,
            guild = GuildConfig(homeTeleportSafetyCheck = false),
        )
        every { guilds.getHome(guild.id, any()) } returns null
        every { guilds.getHomes(guild.id) } returns GuildHomes.EMPTY
        every { guilds.setHome(guild.id, any(), any(), player.uniqueId) } returns true

        val activation = mockk<GuildHomeActivationService>()
        every { activation.persistLocation(any(), guild.id, any(), player.uniqueId, any(), any(), any()) } answers {
            val persist = arg<() -> Boolean>(6)
            if (persist()) HomeActivationCostResult.Applied(0)
            else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        loadKoinModules(module { single { activation } })
        return guilds to activation
    }

    @Test
    fun `Java GUI sets unclaimed Nether guild home with no claim service`() {
        val (guilds, activation) = installPaidHomeFixture()
        val world = (player.server as org.mockbukkit.mockbukkit.ServerMock).addSimpleWorld("java-nether")
        player.teleport(Location(world, 30.0, 75.0, -8.0))
        val menu = io.mockk.spyk(GuildHomeMenu(MenuNavigator(player), player, guild))
        every { menu.open() } returns Unit // Do not render the full menu after the successful write.

        GuildHomeMenu::class.java.getDeclaredMethod("setGuildHome", String::class.java)
            .apply { isAccessible = true }
            .invoke(menu, "nether")

        verify(exactly = 1) {
            activation.persistLocation(
                any(), guild.id, "nether", player.uniqueId, false,
                match { it.worldId == world.uid && it.position == Position3D(30, 75, -8) },
                any(),
            )
        }
        verify(exactly = 1) { guilds.setHome(guild.id, "nether", any(), player.uniqueId) }
    }

    @Test
    fun `Bedrock GUI sets unclaimed End guild home with no claim service`() {
        val (guilds, activation) = installPaidHomeFixture()
        val world = (player.server as org.mockbukkit.mockbukkit.ServerMock).addSimpleWorld("bedrock-end")
        player.teleport(Location(world, 6.0, 68.0, 15.0))
        val menu = BedrockGuildHomeMenu(
            mockk<MenuNavigator>(relaxed = true), player, guild, Logger.getLogger("BedrockHomeAnywhereTest"),
        )

        BedrockGuildHomeMenu::class.java.getDeclaredMethod("showSetHomeMenu")
            .apply { isAccessible = true }
            .invoke(menu)

        verify(exactly = 1) {
            activation.persistLocation(
                any(), guild.id, "home1", player.uniqueId, false,
                match { it.worldId == world.uid && it.position == Position3D(6, 68, 15) },
                any(),
            )
        }
        verify(exactly = 1) { guilds.setHome(guild.id, "home1", any(), player.uniqueId) }
        assertEquals(null, koinClaimProviderOrNull())
    }

    @Test
    fun `Java GUI unsafe location stops before paid activation`() {
        val (guilds, activation) = installPaidHomeFixture()
        val config = GlobalContext.get().get<ConfigService>()
        every { config.loadConfig() } returns MainConfig(
            claimsEnabled = true, guild = GuildConfig(homeTeleportSafetyCheck = true),
        )
        val world = (player.server as org.mockbukkit.mockbukkit.ServerMock).addSimpleWorld("lava-java")
        val unsafe = Location(world, 10.0, 69.0, 10.0)
        unsafe.block.type = org.bukkit.Material.LAVA
        player.teleport(unsafe)
        val menu = io.mockk.spyk(GuildHomeMenu(MenuNavigator(player), player, guild))
        every { menu.open() } returns Unit

        GuildHomeMenu::class.java.getDeclaredMethod("setGuildHome", String::class.java)
            .apply { isAccessible = true }.invoke(menu, "main")

        verify(exactly = 0) {
            activation.persistLocation(any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 0) { guilds.setHome(any(), any(), any(), any()) }
    }

    @Test
    fun `Bedrock GUI unsafe location stops before paid activation`() {
        val (guilds, activation) = installPaidHomeFixture()
        val config = GlobalContext.get().get<ConfigService>()
        every { config.loadConfig() } returns MainConfig(
            claimsEnabled = true, guild = GuildConfig(homeTeleportSafetyCheck = true),
        )
        val world = (player.server as org.mockbukkit.mockbukkit.ServerMock).addSimpleWorld("lava-bedrock")
        val unsafe = Location(world, 10.0, 69.0, 10.0)
        unsafe.block.type = org.bukkit.Material.LAVA
        player.teleport(unsafe)
        val menu = BedrockGuildHomeMenu(
            mockk<MenuNavigator>(relaxed = true), player, guild, Logger.getLogger("BedrockUnsafeHomeTest"),
        )

        BedrockGuildHomeMenu::class.java.getDeclaredMethod("showSetHomeMenu")
            .apply { isAccessible = true }.invoke(menu)

        verify(exactly = 0) {
            activation.persistLocation(any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 0) { guilds.setHome(any(), any(), any(), any()) }
    }

    private fun koinClaimProviderOrNull(): net.lumalyte.lg.application.actions.claim.GetClaimAtPosition? =
        GlobalContext.get().getOrNull()
}
