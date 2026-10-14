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

private const val READER_INVENTORY_SIZE = 54
private const val OTHER_INVENTORY_SIZE = 27

internal class RecruitmentReaderInventoryTest {
    private var fixture: Pair<org.mockbukkit.mockbukkit.ServerMock, org.mockbukkit.mockbukkit.entity.PlayerMock>? = null
    private val server get() = requireNotNull(fixture).first
    private val player get() = requireNotNull(fixture).second
    private val content = RecruitmentBookContent("Details", listOf("Answer"), "Back")

    @BeforeEach
    fun setup() {
        stopKoin()
        val mockServer = MockBukkit.mock()
        fixture = mockServer to mockServer.addPlayer()
        // InventoryFramework caches listener registration across disposable mock servers.
        com.github.stefvanschie.inventoryframework.gui.type.util.Gui::class.java
            .getDeclaredField("hasRegisteredListeners")
            .apply { isAccessible = true }
            .setBoolean(null, false)
        val plugin = MockBukkit.createMockPlugin()
        PluginKeys.initialize(plugin)
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        val lang = mockk<LangService>()
        every { lang.raw(any()) } answers { firstArg() }
        every { lang.msg("recruitment.feedback.failed") } returns net.kyori.adventure.text.Component.empty()
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
        RecruitmentReader(player, content, { true }) {
            returns++
        }.open(false, false)
        assertEquals(READER_INVENTORY_SIZE, player.openInventory.topInventory.size)
        player.closeInventory(InventoryCloseEvent.Reason.PLAYER)
        server.scheduler.performOneTick()
        assertEquals(1, returns)
        server.scheduler.performOneTick()
        assertEquals(1, returns)
    }

    @Test
    fun replacementDoesNotReopen() {
        var returns = 0
        RecruitmentReader(player, content, { true }) {
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
        RecruitmentReader(player, content, { authorized }) {
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
        RecruitmentReader(player, content, { true }) {
            returns++
        }.open(false, false)
        player.closeInventory(InventoryCloseEvent.Reason.PLAYER)
        player.openInventory(server.createInventory(null, OTHER_INVENTORY_SIZE))
        server.scheduler.performOneTick()
        assertEquals(0, returns)
        assertEquals(OTHER_INVENTORY_SIZE, player.openInventory.topInventory.size)
    }
}
