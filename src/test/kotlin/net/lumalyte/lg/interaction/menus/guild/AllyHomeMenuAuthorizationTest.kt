package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.services.TeleportationService
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID

internal class AllyHomeMenuAuthorizationTest {
    @BeforeEach fun setup() = stopKoin()

    @AfterEach fun cleanup() {
        stopKoin()
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test fun revokedAccessDenied() {
        val fixture = Fixture()
        fixture.allowed = false
        fixture.click()
        verify(exactly = 0) { fixture.teleports.startTeleport(any(), any(), any()) }
    }

    @Test fun removedHomeDenied() {
        val fixture = Fixture()
        fixture.currentTarget = fixture.currentTarget?.copy(allyHome = null)
        fixture.click()
        verify(exactly = 0) { fixture.teleports.startTeleport(any(), any(), any()) }
    }

    @Test fun disbandedGuildDenied() {
        val fixture = Fixture()
        fixture.currentTarget = null
        fixture.click()
        verify(exactly = 0) { fixture.teleports.startTeleport(any(), any(), any()) }
    }

    @Test fun relocatedHomeUsesCurrent() {
        val fixture = Fixture()
        fixture.currentTarget =
            fixture.currentTarget?.copy(
                allyHome = GuildHome(fixture.world.uid, Position3D(NEW_X, HOME_Y, NEW_Z)),
            )
        fixture.click()
        verify(exactly = 1) {
            fixture.teleports.startTeleport(
                fixture.player,
                match { it.x == NEW_X + CENTER && it.y == HOME_Y.toDouble() && it.z == NEW_Z + CENTER },
                any(),
            )
        }
    }

    @Test fun renamedGuildKeepsIdentity() {
        val fixture = Fixture()
        fixture.currentTarget = fixture.currentTarget?.copy(name = "Renamed")
        fixture.click()
        verify(exactly = 1) { fixture.teleports.startTeleport(fixture.player, any<Location>(), any()) }
    }

    @Test fun grantedAccessUsesCurrentState() {
        val fixture = Fixture(initialAllowed = false)
        fixture.allowed = true
        fixture.click()
        verify(exactly = 1) { fixture.teleports.startTeleport(fixture.player, any<Location>(), any()) }
    }

    @Test fun unlistedHomeDenied() {
        val fixture = Fixture()
        fixture.eligible = false
        fixture.click()
        verify(exactly = 0) { fixture.teleports.startTeleport(any(), any(), any()) }
    }

    private class Fixture(initialAllowed: Boolean = true) {
        val server = MockBukkit.mock()
        val world = server.addSimpleWorld("world")
        val player = server.addPlayer()
        val teleports = mockk<TeleportationService>(relaxed = true)
        var allowed = initialAllowed
        var eligible = true
        var currentTarget: Guild? =
            Guild(
                id = UUID.randomUUID(),
                name = "Ally",
                createdAt = Instant.now(),
                allyHome = GuildHome(world.uid, Position3D(OLD_X, HOME_Y, OLD_Z)),
            )
        private val source = Guild(id = UUID.randomUUID(), name = "Source", createdAt = Instant.now())
        private val targetId = requireNotNull(currentTarget).id
        private val guilds = mockk<GuildService>(relaxed = true)
        private val config = mockk<ConfigService>()
        private val pane = StaticPane(0, 0, PANE_COLUMNS, PANE_ROWS)

        init {
            val plugin = MockBukkit.createMockPlugin()
            mockkStatic(JavaPlugin::class)
            every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
            val lang = mockk<LangService>(relaxed = true)
            every { lang.msg(any()) } returns Component.text("Home")
            every { lang.msg(any(), any()) } returns Component.text("Home")
            every { guilds.getAllyHomes(source.id) } answers { eligibleHomes() }
            every { guilds.getGuildByName("Ally") } returns currentTarget
            every { guilds.getGuild(targetId) } answers { currentTarget }
            every { guilds.canUseAllyHome(player.uniqueId, source.id, targetId) } answers { allowed }
            every { config.loadConfig() } returns MainConfig().also { it.guild.homeTeleportSafetyCheck = false }
            startKoin {
                modules(
                    module {
                        single { guilds }
                        single { config }
                        single { teleports }
                        single { lang }
                    },
                )
            }
            val menu = GuildHomeMenu(mockk(relaxed = true), player, source)
            GuildHomeMenu::class.java
                .getDeclaredMethod(
                    "addAllyHomeButtons",
                    StaticPane::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .also { it.isAccessible = true }
                .invoke(menu, pane, 0, ALLY_ROW)
        }

        fun click() = requireNotNull(pane.getItem(0, ALLY_ROW)).callAction(mockk(relaxed = true))

        private fun eligibleHomes(): Map<String, GuildHome> {
            val target = currentTarget ?: return emptyMap()
            val home = target.allyHome ?: return emptyMap()
            return if (eligible) mapOf(target.name to home) else emptyMap()
        }
    }
}

private const val OLD_X = 10
private const val OLD_Z = 20
private const val HOME_Y = 70
private const val NEW_X = 100
private const val NEW_Z = 200
private const val CENTER = 0.5

private const val PANE_COLUMNS = 9
private const val PANE_ROWS = 6
private const val ALLY_ROW = 5
