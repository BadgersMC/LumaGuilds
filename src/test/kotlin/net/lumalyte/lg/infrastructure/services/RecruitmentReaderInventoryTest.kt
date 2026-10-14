package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.common.PluginKeys
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertEquals

internal class RecruitmentReaderInventoryTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    private lateinit var player: org.mockbukkit.mockbukkit.entity.PlayerMock

    @BeforeEach
    fun setup() {
        stopKoin()
        server = MockBukkit.mock()
        // InventoryFramework caches listener registration across disposable mock servers.
        com.github.stefvanschie.inventoryframework.gui.type.util.Gui::class.java
            .getDeclaredField("hasRegisteredListeners").apply { isAccessible = true }.setBoolean(null, false)
        player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin()
        PluginKeys.initialize(plugin)
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        val lang = mockk<LangService>()
        every { lang.raw(any()) } answers { firstArg() }
        every { lang.msg(any(), *anyVararg()) } returns net.kyori.adventure.text.Component.empty()
        startKoin { modules(module { single { lang } }) }
    }

    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    @Test
    fun escapeReturnsOnce() {
        var returns = 0
        RecruitmentReader(player, RecruitmentBookContent("Details", listOf("Answer"), "Back"), { true }) {
            returns++
        }.open(false, false)
        assertEquals(54, player.openInventory.topInventory.size)
        player.closeInventory(InventoryCloseEvent.Reason.PLAYER)
        server.scheduler.performOneTick()
        assertEquals(1, returns)
        server.scheduler.performOneTick()
        assertEquals(1, returns)
    }

    @Test
    fun replacementDoesNotReopen() {
        var returns = 0
        RecruitmentReader(player, RecruitmentBookContent("Details", listOf("Answer"), "Back"), { true }) {
            returns++
        }.open(false, false)
        player.closeInventory(InventoryCloseEvent.Reason.OPEN_NEW)
        server.scheduler.performOneTick()
        assertEquals(0, returns)
    }

    @Test
    fun revokedAccessCannotReturn() {
        var authorized = true
        var returns = 0
        RecruitmentReader(player, RecruitmentBookContent("Details", listOf("Answer"), "Back"), { authorized }) {
            returns++
        }.open(false, false)
        player.closeInventory(InventoryCloseEvent.Reason.PLAYER)
        authorized = false
        server.scheduler.performOneTick()
        assertEquals(0, returns)
    }

    @Test
    fun delayedReturnRespectsNewMenu() {
        var returns = 0
        RecruitmentReader(player, RecruitmentBookContent("Details", listOf("Answer"), "Back"), { true }) {
            returns++
        }.open(false, false)
        player.closeInventory(InventoryCloseEvent.Reason.PLAYER)
        player.openInventory(server.createInventory(null, 27))
        server.scheduler.performOneTick()
        assertEquals(0, returns)
        assertEquals(27, player.openInventory.topInventory.size)
    }
}
