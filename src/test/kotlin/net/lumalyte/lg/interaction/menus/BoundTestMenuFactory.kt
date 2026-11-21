package net.lumalyte.lg.interaction.menus

import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.interaction.menus.bedrock.BedrockMenuDependencies
import java.util.logging.Logger

/** Explicit collaborators for standalone factory routing tests, without a global container. */
internal fun boundTestMenuFactory(
    platformDetectionService: PlatformDetectionService,
    configService: ConfigService,
    logger: Logger,
    lang: LangService,
    bedrockDependencyProvider: ((MenuFactory) -> BedrockMenuDependencies)? = null,
    chatServiceProvider: (() -> ChatService)? = null,
    menuServicesProvider: ((MenuFactory) -> MenuServices)? = null,
): MenuFactory = MenuFactory(
    platformDetectionService, configService, logger, lang,
    bedrockDependencyProvider = bedrockDependencyProvider ?: { owner ->
        BedrockMenuDependencies(owner, mockk(relaxed = true), mockk(relaxed = true), lang,
            mockk(relaxed = true), mockk(relaxed = true), configService)
    },
    chatServiceProvider = chatServiceProvider,
    menuServicesProvider = menuServicesProvider ?: { owner ->
        testMenuServices(owner, mapOf(LangService::class to lang, ConfigService::class to configService))
    },
)
