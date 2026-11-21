package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.GuildGettingStartedService
import net.lumalyte.lg.interaction.menus.MenuFactory

/**
 * Dependencies are supplied by the active container at the MenuFactory boundary.
 * Shared Java and Bedrock navigation never accesses either DI framework.
 */
internal data class GuildGettingStartedNavigationDependencies(
    val service: GuildGettingStartedService,
    val menuFactory: MenuFactory,
    val prompts: GuildOnboardingRepository,
    val lang: LangService,
)
