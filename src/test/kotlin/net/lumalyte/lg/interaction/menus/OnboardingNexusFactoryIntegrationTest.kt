package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.application.services.GuildGettingStartedService
import net.lumalyte.lg.application.services.GuildStartSnapshot
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildGettingStartedMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockMenuDependencies
import net.lumalyte.lg.interaction.menus.guild.GuildGettingStartedMenu
import net.lumalyte.lg.interaction.menus.guild.GuildGettingStartedNavigation
import net.lumalyte.lg.interaction.menus.guild.GuildGettingStartedNavigationDependencies
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Both front ends must construct through the Nexus-compatible graph without Koin. */
class OnboardingNexusFactoryIntegrationTest {
    @Test
    fun verifiesKoinFreeOnboarding() {
        assertFalse(KoinComponent::class.java.isAssignableFrom(GuildGettingStartedNavigation::class.java))
        val playerId = UUID.randomUUID()
        val player = mockk<Player>(relaxed = true) {
            every { uniqueId } returns playerId
        }
        val navigator = mockk<MenuNavigator>(relaxed = true)
        val service = mockk<GuildGettingStartedService>()
        every { service.snapshot(playerId, null) } returns GuildStartSnapshot(
            null, emptyList(), 0, 0,
        )
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        }
        val prompts = mockk<GuildOnboardingRepository>()
        fun factory(bedrock: Boolean): MenuFactory {
            val config = mockk<ConfigService> {
                every { loadConfig() } returns MainConfig().apply {
                    this.bedrock.bedrockMenusEnabled = bedrock
                    this.bedrock.forceBedrockMenus = bedrock
                }
            }
            return MenuFactory(
                mockk<PlatformDetectionService>(), config, Logger.getAnonymousLogger(), lang,
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
            ).apply {
                bindGettingStartedDependencies { owner ->
                    GuildGettingStartedNavigationDependencies(service, owner, prompts, lang)
                }
            }
        }

        assertIs<GuildGettingStartedMenu>(factory(false).createGuildGettingStartedMenu(navigator, player, null))
        val bedrockMenu = assertIs<BedrockGuildGettingStartedMenu>(
            factory(true).createGuildGettingStartedMenu(navigator, player, null)
        )
        assertNotNull(bedrockMenu.getForm())
        verify(exactly = 1) { service.snapshot(playerId, null) }
    }
}
