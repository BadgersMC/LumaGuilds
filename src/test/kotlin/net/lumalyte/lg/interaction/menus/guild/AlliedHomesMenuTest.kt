package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
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
import kotlin.test.assertEquals

/** REQ-180: the guild sets, moves and removes its allied homes from the menu. */
class AlliedHomesMenuTest {
    @BeforeEach fun setup() = stopKoin()

    @AfterEach fun cleanup() {
        stopKoin()
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    private val server = MockBukkit.mock()
    private val player = server.addPlayer()
    private val guild = Guild(UUID.randomUUID(), "Host", createdAt = Instant.now())
    private val guilds = mockk<GuildService>(relaxed = true)

    private fun pane(slots: Int, home1: GuildHome? = null): net.lumalyte.lg.utils.inventoryframework.StaticPane {
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        val lang = mockk<LangService>(relaxed = true)
        every { lang.msg(any()) } returns Component.text("msg")
        every { lang.msg(any(), any()) } returns Component.text("msg")
        every { guilds.allyHomeSlots(guild.id) } returns slots
        every { guilds.getAllyHome(guild.id, 1) } returns home1
        every { guilds.getAllyHome(guild.id, 2) } returns null
        every { guilds.setAllyHome(any(), any(), any(), any()) } returns true
        every { guilds.removeAllyHome(any(), any(), any()) } returns true
        startKoin { modules(module { single { guilds }; single { lang }; single { mockk<MenuFactory>(relaxed = true) } }) }
        return AlliedHomesMenu(mockk<MenuNavigator>(relaxed = true), player, guild).buildPane()
    }

    private fun clickEvent(right: Boolean): InventoryClickEvent = mockk(relaxed = true) {
        every { isRightClick } returns right
    }

    @Test
    fun `left-click sets allied home 1 where the player stands`() {
        val p = pane(slots = 1)
        requireNotNull(p.getItem(1, 0)).callAction(clickEvent(right = false))
        val at = player.location
        verify { guilds.setAllyHome(guild.id, GuildHome(at.world.uid, Position3D(at.blockX, at.blockY, at.blockZ)), player.uniqueId, 1) }
    }

    @Test
    fun `right-click removes a set allied home`() {
        val p = pane(slots = 1, home1 = GuildHome(UUID.randomUUID(), Position3D(1, 2, 3)))
        requireNotNull(p.getItem(1, 0)).callAction(clickEvent(right = true))
        verify { guilds.removeAllyHome(guild.id, player.uniqueId, 1) }
    }

    @Test
    fun `slot 2 is locked below Prestige II and usable at it`() {
        assertEquals(Material.BARRIER, pane(slots = 1).getItem(3, 0)!!.item.type)
        stopKoin()
        val p = pane(slots = 2)
        requireNotNull(p.getItem(3, 0)).callAction(clickEvent(right = false))
        verify { guilds.setAllyHome(guild.id, any(), player.uniqueId, 2) }
    }
}
