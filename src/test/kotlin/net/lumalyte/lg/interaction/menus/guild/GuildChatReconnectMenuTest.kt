package net.lumalyte.lg.interaction.menus.guild

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.lumalyte.lg.application.persistence.GuildChatReconnectSettingsRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildChatRankSettingsService
import net.lumalyte.lg.application.services.GuildChatReconnectSettingsService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildSettingsMenu
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.impl.FormDefinition
import org.geysermc.cumulus.form.impl.FormDefinitions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import java.util.logging.Logger
import kotlin.properties.Delegates
import kotlin.test.assertEquals

/** Real Java actions and serialized Bedrock responses share the authorized preference service. */
// Four client regressions share isolated lifecycle, language and form fixtures.
@Suppress("TooManyFunctions")
internal class GuildChatReconnectMenuTest {
    @TempDir var directory: Path? = null
    private var server: ServerMock by Delegates.notNull()
    private var player: PlayerMock by Delegates.notNull()
    private val guild = Guild(UUID.randomUUID(), "Reconnect", createdAt = Instant.EPOCH)
    private val guilds = mockk<GuildService>(relaxed = true)
    private val repository = mockk<GuildChatReconnectSettingsRepository>()
    private val rankSettings = mockk<GuildChatRankSettingsService>()
    private val navigator = mockk<MenuNavigator>(relaxed = true)
    private var allowed = true

    /** Initializes only local MockBukkit/Koin; no Floodgate or live player is needed. */
    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        every { guilds.getGuild(guild.id) } returns guild
        every { guilds.hasPermission(any(), guild.id, any()) } answers { allowed }
        every { repository.resetOnJoin(guild.id) } returns false
        every { repository.compareAndSet(guild.id, false, true) } returns true
        every { rankSettings.ranksVisible(guild.id) } returns true
        initializeServices(plugin)
    }

    private fun initializeServices(plugin: Plugin) {
        val service = GuildChatReconnectSettingsService(repository, guilds)
        val language = createLanguage()
        val config = mockk<ConfigService> { every { loadConfig() } returns MainConfig() }
        stopKoin()
        startKoin {
            modules(
                module {
                    single<Plugin> { plugin }
                    single { service }
                    single { language }
                    single { config }
                    single { rankSettings }
                    single { guilds }
                },
            )
        }
    }

    private fun createLanguage(): LangService {
        val host = object : LangHost {
            override val dataFolder: File = checkNotNull(directory).toFile()
            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
        }
        return LangService(host, Locale("en_US"), LumaGuildsLang::class.java)
    }

    /** Restores process-wide test resources. */
    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    /** Java toggles call the authorized service and reopen after a successful save. */
    @Test
    fun javaToggle() {
        val menu = javaMenu()
        val pane = StaticPane(0, 0, MENU_COLUMNS, MENU_ROWS)
        menu.javaClass
            .getDeclaredMethod("addReconnectControl", StaticPane::class.java)
            .apply { isAccessible = true }
            .invoke(menu, pane)
        val item = pane.items.single()
        assertEquals(Material.GRAY_DYE, item.item.type)
        item.callAction(mockk(relaxed = true))
        verify(exactly = 1) { repository.compareAndSet(guild.id, false, true) }
        verify(exactly = 1) { menu.open() }
    }

    /** Serialized Bedrock field ordering saves the new preference on the server thread. */
    @Test
    fun bedrockToggle() {
        val form = bedrock().getForm() as CustomForm
        respond(form, true)
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
        server.scheduler.performOneTick()
        verify(exactly = 1) { repository.compareAndSet(guild.id, false, true) }
        verify(exactly = 0) { guilds.setGuiTheme(any(), any(), any()) }
    }

    /** A manager who loses authority while a form is open cannot save the toggle. */
    @Test
    fun revokedBedrockForm() {
        val form = bedrock().getForm() as CustomForm
        allowed = false
        respond(form, true)
        server.scheduler.performOneTick()
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
    }

    /** An unchanged old form cannot restore a newer preference. */
    @Test
    fun staleUnchangedBedrockForm() {
        val form = bedrock().getForm() as CustomForm
        every { repository.resetOnJoin(guild.id) } returns true
        respond(form, false)
        server.scheduler.performOneTick()
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
    }

    private fun javaMenu(): GuildSettingsMenu {
        val menu =
            spyk(
                GuildSettingsMenu(
                    navigator,
                    player,
                    guild,
                    guilds,
                    mockk(),
                    mockk(),
                    mockk(),
                    mockk(),
                    mockk(),
                ),
            )
        every { menu.open() } just Runs
        return menu
    }

    private fun bedrock() =
        spyk(BedrockGuildSettingsMenu(navigator, player, guild, Logger.getLogger("ReconnectMenuTest"))).also {
            // Transport is external; validation and submitted form handlers remain real.
            every { it.reopen() } just Runs
        }

    private fun respond(form: CustomForm, enabled: Boolean) {
        val definition: FormDefinition<CustomForm, *, *> = FormDefinitions.instance().definitionFor(form)
        definition.handleFormResponse(
            form,
            """[null,"Reconnect","",0,${guild.isOpen},${guild.trackingEnabled},true,$enabled,0,null]""",
        )
    }
    private companion object {
        const val MENU_COLUMNS = 9
        const val MENU_ROWS = 6
    }
}
