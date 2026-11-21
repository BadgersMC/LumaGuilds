package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BedrockLocalizationService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.FormCacheService
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.plugin.Plugin

/**
 * Explicit dependency boundary for Bedrock menu instances.
 * Built by the owning DI context, never resolved from a global container.
 */
data class BedrockMenuDependencies(
    val menuFactory: MenuFactory,
    val localization: BedrockLocalizationService,
    val plugin: Plugin,
    val lang: LangService,
    val cache: FormCacheService,
    val validation: FormValidationService,
    val config: ConfigService,
)
