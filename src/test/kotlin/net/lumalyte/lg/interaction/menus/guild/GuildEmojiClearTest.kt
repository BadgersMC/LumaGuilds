package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNull

class GuildEmojiClearTest {
    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any()) } returns plugin
        stopKoin()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkStatic(JavaPlugin::class)
        MockBukkit.unmock()
    }

    @Test
    fun `Java clear button persists removal immediately`() {
        val guildId = UUID.randomUUID()
        var storedEmoji: String? = ":enthusia:"
        val guildService = mockk<GuildService>(relaxed = true) {
            every { getEmoji(guildId) } answers { storedEmoji }
            every { setEmoji(guildId, null, any()) } answers {
                storedEmoji = null
                true
            }
        }
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } returns Component.text("localized")
            every { raw(any()) } returns "localized"
        }
        val nexoEmojiService = mockk<NexoEmojiService>(relaxed = true)

        startKoin {
            modules(module {
                single<GuildService> { guildService }
                single<LangService> { lang }
                single<NexoEmojiService> { nexoEmojiService }
                single<ChatInputListener> { mockk(relaxed = true) }
                single<MenuFactory> { mockk(relaxed = true) }
                single<MenuItemBuilder> { mockk(relaxed = true) }
            })
        }

        val player = server.addPlayer()
        val guild = Guild(id = guildId, name = "Clearable", emoji = storedEmoji, createdAt = Instant.EPOCH)
        val menu = GuildEmojiMenu(mockk<MenuNavigator>(relaxed = true), player, guild)
        val pane = com.github.stefvanschie.inventoryframework.pane.StaticPane(9, 3)
        val addClearButton = GuildEmojiMenu::class.java.getDeclaredMethod(
            "addClearButton",
            com.github.stefvanschie.inventoryframework.pane.StaticPane::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        addClearButton.invoke(menu, pane, 4, 2)
        pane.items.single().callAction(mockk(relaxed = true))

        assertNull(storedEmoji)
    }
}
