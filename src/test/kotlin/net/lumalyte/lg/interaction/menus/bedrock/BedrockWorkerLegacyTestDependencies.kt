package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.di.legacyMenuServices
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.plugin.Plugin
import org.koin.core.context.GlobalContext
import java.util.logging.Logger

/** Adapts existing isolated Koin-based behavior fixtures to the new explicit Bedrock constructor. */
@OptIn(org.koin.core.annotation.KoinInternalApi::class)
internal fun bedrockWorkerLegacyDependencies(): BedrockMenuDependencies {
    val koin = GlobalContext.get()
    val config = koin.getOrNull<ConfigService>() ?: mockk<ConfigService>().also {
        every { it.loadConfig() } returns MainConfig()
    }
    val lang = koin.get<LangService>()
    val factory = MenuFactory(
        mockk<PlatformDetectionService>(relaxed = true), config, Logger.getAnonymousLogger(), lang,
        menuServicesProvider = { owner -> koin.scopeRegistry.rootScope.legacyMenuServices(owner) },
    )
    return BedrockMenuDependencies(
        menuFactory = factory,
        localization = koin.getOrNull() ?: mockk(relaxed = true),
        plugin = koin.getOrNull<Plugin>() ?: mockk(relaxed = true),
        lang = lang,
        cache = koin.getOrNull() ?: mockk(relaxed = true),
        validation = koin.getOrNull() ?: mockk(relaxed = true),
        config = config,
    )
}
