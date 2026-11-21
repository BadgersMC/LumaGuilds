package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.application.services.GuildListPage
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NexusBedrockFactoryWiringTest {
    @Test
    fun `Nexus factory builds confirmations chat settings and directory without Koin`() {
        val id = UUID.randomUUID()
        val player = mockk<Player>(relaxed = true) { every { uniqueId } returns id }
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val config = mockk<ConfigService> {
            every { loadConfig() } returns MainConfig().apply {
                bedrock.bedrockMenusEnabled = true
                bedrock.forceBedrockMenus = true
            }
        }
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        }
        val chat = mockk<ChatService> {
            every { getVisibilitySettings(id) } returns ChatVisibilitySettings(id)
        }
        val directory = mockk<GuildListService> {
            every { configuredPageSize() } returns 10
            every { getPage(any(), any(), any(), any()) } returns GuildListPage(
                entries = emptyList(),
                page = 0,
                pageSize = 10,
                totalCount = 0,
                totalPages = 1,
                sortKey = GuildListSortKey.ALL_TIME_ACTIVE,
                ascending = false,
            )
        }
        var chatResolves = 0
        var directoryResolves = 0
        val factory = MenuFactory(
            mockk<PlatformDetectionService>(),
            config,
            Logger.getAnonymousLogger(),
            lang,
            bedrockDependencyProvider = { owner ->
                BedrockMenuDependencies(
                    menuFactory = owner,
                    localization = mockk<BedrockLocalizationService>(),
                    plugin = mockk<Plugin>(),
                    lang = lang,
                    cache = mockk<FormCacheService>(),
                    validation = mockk<FormValidationService>(),
                    config = config,
                )
            },
            chatServiceProvider = { chatResolves++; chat },
        ).apply {
            bindGuildListService { directoryResolves++; directory }
        }

        val confirmation = assertIs<BedrockConfirmationMenu>(
            factory.createConfirmationMenu(navigator, player, "Confirm") {}
        )
        val enhanced = assertIs<BedrockConfirmationMenu>(
            factory.createConfirmationMenu(navigator, player, "Confirm", "Message", {})
        )
        val chatMenu = assertIs<BedrockPlayerChatSettingsMenu>(
            factory.createPlayerChatSettingsMenu(navigator, player)
        )
        val directoryMenu = assertIs<BedrockGuildListMenu>(
            factory.createGuildListMenu(navigator, player)
        )
        assertEquals(0, chatResolves, "ChatService stays lazy until the form is rendered")
        assertEquals(0, directoryResolves, "GuildListService stays lazy until the form is rendered")

        assertNotNull(confirmation.getForm())
        assertNotNull(enhanced.getForm())
        assertNotNull(chatMenu.getForm())
        assertNotNull(directoryMenu.getForm())

        assertEquals(1, chatResolves)
        assertEquals(1, directoryResolves)
        verify(exactly = 1) { chat.getVisibilitySettings(id) }
        verify(exactly = 1) { directory.getPage(0, 10, GuildListSortKey.ALL_TIME_ACTIVE, any()) }
    }
}
