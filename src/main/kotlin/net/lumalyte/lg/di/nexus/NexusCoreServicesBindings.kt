package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.application.services.GuildGettingStartedService
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.infrastructure.services.BedrockLocalizationServiceFloodgate
import net.lumalyte.lg.infrastructure.services.FloodgatePlatformDetectionService
import net.lumalyte.lg.infrastructure.services.FormCacheServiceGuava
import net.lumalyte.lg.infrastructure.services.FormValidationServiceImpl
import net.lumalyte.lg.infrastructure.services.PlayerMetadataServiceVault
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.guild.GuildGettingStartedNavigationDependencies
import net.lumalyte.lg.interaction.menus.bedrock.BedrockMenuDependencies
import org.bukkit.plugin.Plugin
import net.lumalyte.lg.utils.MenuItemBuilder
import net.milkbowl.vault.chat.Chat
import java.util.logging.Logger

/**
 * Service-level replacements for legacy coreModule bindings.
 * Nexus itself owns the virtual-thread executor/scope; the old named Koin executor
 * and dispatcher aliases are intentionally NOT recreated here.
 */
internal fun NexusContext.registerCoreServiceBindings(plugin: LumaGuilds) {
    // Vault Chat is optional. Do not register a nullable singleton in Nexus.
    plugin.metadata?.let { chat ->
        registerBean("vaultChat", Chat::class, chat)
        registerBean("playerMetadataService", PlayerMetadataService::class) {
            PlayerMetadataServiceVault(
                getBean<Chat>(),
                getBean<MainConfig>(),
                requireNotNull(dispatchers).virtualThread
            )
        }
    }

    registerBean("platformDetectionService", PlatformDetectionService::class) {
        FloodgatePlatformDetectionService(getBean<Logger>())
    }
    registerBean("bedrockLocalizationService", BedrockLocalizationService::class) {
        BedrockLocalizationServiceFloodgate(getBean<Logger>())
    }
    registerBean("formCacheService", FormCacheService::class) {
        val config = getBean<ConfigService>().loadConfig().bedrock
        FormCacheServiceGuava(
            maxCacheSize = config.formCacheSize,
            cacheExpirationMinutes = config.formCacheExpirationMinutes,
            logger = getBean<Logger>()
        )
    }
    registerBean("formValidationService", FormValidationService::class) {
        FormValidationServiceImpl(getBean<Logger>())
    }
    registerBean("menuItemBuilder", MenuItemBuilder::class) {
        MenuItemBuilder(getBean<ConfigService>(), getBean<LangService>())
    }
    registerBean("menuFactory", MenuFactory::class) {
        MenuFactory(
            getBean<PlatformDetectionService>(),
            getBean<ConfigService>(),
            getBean<Logger>(),
            getBean<LangService>(),
            bedrockDependencyProvider = { factory ->
                BedrockMenuDependencies(
                    menuFactory = factory,
                    localization = getBean<BedrockLocalizationService>(),
                    plugin = getBean<Plugin>(),
                    lang = getBean<LangService>(),
                    cache = getBean<FormCacheService>(),
                    validation = getBean<FormValidationService>(),
                    config = getBean<ConfigService>(),
                )
            },
            chatServiceProvider = { getBean<ChatService>() },
        ).apply {
            bindGuildListService { getBean<GuildListService>() }
            bindGettingStartedDependencies { factory ->
                GuildGettingStartedNavigationDependencies(
                    service = getBean<GuildGettingStartedService>(),
                    menuFactory = factory,
                    prompts = getBean<GuildOnboardingRepository>(),
                    lang = getBean<LangService>(),
                )
            }
        }
    }
}
