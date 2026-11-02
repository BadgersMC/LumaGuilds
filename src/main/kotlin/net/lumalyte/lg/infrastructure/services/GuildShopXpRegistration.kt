package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.GuildShopXpApi
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.ServicePriority

/** Publish the provider even when XP is disabled so Market can finish non-awarding sales. */
internal fun registerGuildShopXpApi(
    plugin: Plugin,
    repository: GuildShopXpRepository,
    config: ConfigService,
    progression: ProgressionService,
    completedSale: (java.util.UUID, java.util.UUID) -> Unit = { _, _ -> },
) {
    plugin.server.servicesManager.register(
        GuildShopXpApi::class.java,
        GuildShopXpProvider(repository, config, progression, completedSale),
        plugin,
        ServicePriority.Normal,
    )
}
