package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.DiscordAccountLinkSubscription
import net.lumalyte.lg.application.services.DiscordGuildProfileSubscription
import net.lumalyte.lg.application.services.GuildDiscordProfileService
import net.lumalyte.lg.application.services.GuildDiscordRoleService
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.infrastructure.services.DiscordSrvAccountLinkSubscription
import net.lumalyte.lg.infrastructure.services.DiscordSrvGuildProfileSubscription
import net.lumalyte.lg.infrastructure.services.SeasonalEloCoordinator
import net.lumalyte.lg.infrastructure.services.UnavailableDiscordAccountLinkSubscription
import net.lumalyte.lg.infrastructure.services.UnavailableDiscordGuildProfileSubscription
import org.bukkit.Bukkit

/**
 * Optional DiscordSRV subscriptions. Constructors accept Executor rather than ExecutorService:
 * they only use CompletableFuture's Executor API, so the Nexus-managed virtual executor
 * replaces the legacy Koin pool without allocating or owning a second pool.
 *
 * DiscordSRV is probed on first lookup, exactly like the legacy Koin factories.
 * Registering these definitions never subscribes listeners or accesses Bukkit.
 */
internal fun NexusContext.registerDiscordBindings() {
    registerBean("discordAccountLinkSubscription", DiscordAccountLinkSubscription::class) {
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
            DiscordSrvAccountLinkSubscription(
                getBean<GuildDiscordRoleService>(),
                nexusVirtualExecutor(),
            )
        } else {
            UnavailableDiscordAccountLinkSubscription()
        }
    }
    registerBean("discordGuildProfileSubscription", DiscordGuildProfileSubscription::class) {
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
            DiscordSrvGuildProfileSubscription(
                plugin = getBean<LumaGuilds>(),
                profiles = getBean<GuildDiscordProfileService>(),
                guildListService = getBean<GuildListService>(),
                killService = getBean<KillService>(),
                warService = getBean<WarService>(),
                seasonalElo = getBean<SeasonalEloCoordinator>(),
                executor = nexusVirtualExecutor(),
            )
        } else {
            UnavailableDiscordGuildProfileSubscription()
        }
    }
}
