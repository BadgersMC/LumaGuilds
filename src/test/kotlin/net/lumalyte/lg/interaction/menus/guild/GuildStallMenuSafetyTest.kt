package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildStallReadService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.StallReadResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BaseBedrockMenu
import org.bukkit.event.inventory.InventoryType
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.impl.FormDefinition
import org.geysermc.cumulus.form.impl.FormDefinitions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Real inventory close/navigation prevents asynchronous Market results reopening menus. */
@Suppress("TooManyFunctions") // Six independent GUI races share small JUnit lifecycle/reflective fixtures.
internal class GuildStallMenuSafetyTest {
    @TempDir var directory: Path? = null
    private var server: ServerMock by Delegates.notNull()
    private var player: PlayerMock by Delegates.notNull()
    private var members: MemberService by Delegates.notNull()
    private var pending: CompletableFuture<StallReadResult> by Delegates.notNull()
    private var navigator: MenuNavigator by Delegates.notNull()
    private var menu: GuildStallMenu by Delegates.notNull()
    private val guild = Guild(UUID.randomUUID(), "Guild", createdAt = Instant.EPOCH)

    /** Initialize a real GUI with a controlled asynchronous read port. */
    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        members = mockk()
        every { members.getMember(player.uniqueId, guild.id) } returns mockk()
        initializeServices(plugin)
        pending = CompletableFuture()
        val client = mockk<GuildStallReadService>()
        every { client.read(guild.id, player.uniqueId) } returns pending
        navigator = MenuNavigator(player)
        menu = GuildStallMenu(navigator, player, guild, false, client)
        navigator.openMenu(menu)
    }

    private fun initializeServices(plugin: Plugin) {
        val lang = createLanguage()
        stopKoin()
        startKoin {
            modules(
                module {
                    single<Plugin> { plugin }
                    single { members }
                    single { lang }
                },
            )
        }
    }

    private fun createLanguage(): LangService {
        return LangService(
        object : LangHost {
            override val dataFolder: File = checkNotNull(directory).toFile()
            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
        },
        Locale("en_US"),
        LumaGuildsLang::class.java,
    )
    }

    /** Restore global test resources. */
    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    /** Escape/close invalidates the loading request. */
    @Test
    fun closeDiscardsLateData() {
        server.pluginManager.callEvent(
            org.bukkit.event.inventory
                .InventoryCloseEvent(player.openInventory),
        )
        player.closeInventory()
        pending.complete(StallReadResult.Available(emptyList()))
        server.scheduler.performOneTick()
        assertNull(player.openInventory.topInventory)
    }

    /** A different command's inventory cannot be replaced by an old request. */
    @Test
    fun newInventoryDiscardsLateData() {
        val replacement = server.createInventory(null, INVENTORY_ROW_SIZE)
        player.openInventory(replacement)
        pending.complete(StallReadResult.Available(emptyList()))
        server.scheduler.performOneTick()
        assertSame(replacement, player.openInventory.topInventory)
    }

    /** Forms opened by a separate command invalidate the original navigator too. */
    @Test
    @DisplayName("Separate navigator discards late data")
    fun separateNavigatorDiscardsData() {
        val loading = player.openInventory.topInventory
        MenuNavigator(player).openMenu(mockk(relaxed = true))
        pending.complete(StallReadResult.Available(emptyList()))
        server.scheduler.performOneTick()
        assertSame(loading, player.openInventory.topInventory)
    }

    /** Leaving the guild before completion makes the read unusable. */
    @Test
    fun departureDiscardsLateData() {
        val loading = player.openInventory.topInventory
        every { members.getMember(player.uniqueId, guild.id) } returns null
        pending.complete(StallReadResult.Available(emptyList()))
        server.scheduler.performOneTick()
        assertSame(loading, player.openInventory.topInventory)
    }

    /** Cumulus responses run on the server scheduler and recheck membership. */
    @Test
    @DisplayName("Bedrock response rechecks membership")
    fun bedrockRechecksMembership() {
        var clicks = 0
        val form = form { clicks++ }
        val definition: FormDefinition<SimpleForm, *, *> = FormDefinitions.instance().definitionFor(form)
        definition.handleFormResponse(form, "0")
        assertEquals(0, clicks)
        server.scheduler.performOneTick()
        assertEquals(1, clicks)
        every { members.getMember(player.uniqueId, guild.id) } returns null
        definition.handleFormResponse(form, "0")
        server.scheduler.performOneTick()
        assertEquals(1, clicks)
    }

    private fun form(action: () -> Unit): SimpleForm {
        val row = formRow(action)
        val formType = GuildStallMenu::class.java.declaredClasses.single { it.simpleName == "StallForm" }
        val view =
            formType.declaredConstructors
                .single()
                .apply { isAccessible = true }
                .newInstance(
                    menu,
                    navigator,
                    player,
                    navigator.currentNavigationToken(),
                    Component.text("Stall"),
                    listOf(row),
                )
                as BaseBedrockMenu
        return view.getForm() as SimpleForm
    }

    private fun formRow(action: () -> Unit): Any {
        val rowType = GuildStallMenu::class.java.declaredClasses.single { it.simpleName == "Row" }
        return rowType.declaredConstructors
            .single()
            .apply { isAccessible = true }
            .newInstance(Component.text("Member"), emptyList<Component>(), action)
    }

    /** A current viewer receives a proper no-stall response. */
    @Test
    fun emptyResponseKeepsMenu() {
        pending.complete(StallReadResult.Available(emptyList()))
        server.scheduler.performOneTick()
        assertEquals(InventoryType.CHEST, player.openInventory.topInventory.type)
    }
    private companion object {
        const val INVENTORY_ROW_SIZE = 9
    }
}
