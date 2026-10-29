package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.GuildInviteBlocks
import net.lumalyte.lg.api.GuildInviteBlocksImpl
import net.lumalyte.lg.api.GuildInviteBlocksV2
import net.lumalyte.lg.api.GuildInviteBlocksV2Impl
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.ServicePriority

/** Registers the unchanged v1 service and advertises the separate v2 contract only on backend-local SQLite. */
internal fun registerGuildInviteBlocks(
    plugin: Plugin,
    dialect: SqlDialect,
    legacy: InviteBlockService,
    durable: DurableInviteBlockService,
) {
    val services = plugin.server.servicesManager
    services.register(GuildInviteBlocks::class.java, GuildInviteBlocksImpl(legacy), plugin, ServicePriority.Normal)
    if (dialect == SqlDialect.SQLITE) {
        services.register(
            GuildInviteBlocksV2::class.java,
            GuildInviteBlocksV2Impl(durable),
            plugin,
            ServicePriority.Normal,
        )
    }
}
