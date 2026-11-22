package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildNotificationListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildRichPresenceListener
import net.lumalyte.lg.infrastructure.listeners.apollo.GuildTeamListener
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService
import net.lumalyte.lg.infrastructure.services.apollo.GuildRichPresenceService
import net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService
import net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService
import net.lumalyte.lg.infrastructure.services.apollo.LunarClientServiceBukkit
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass

/** Exact bean name GuildTeamListener probes for its optional waypoint collaborator. */
internal const val GUILD_WAYPOINT_SERVICE_BEAN = "guildWaypointService"

/** Bukkit plugin name gating every Apollo (Lunar Client) definition, as in Koin. */
private const val APOLLO_PLUGIN_NAME = "Apollo-Bukkit"

/** Register only installed optional APIs. Their services are constructed lazily by the active graph. */
internal fun NexusContext.registerIntegrationBindings(plugin: LumaGuilds) {
    fun installed(name: String): Boolean = try {
        Bukkit.getPluginManager().getPlugin(name) != null
    } catch (e: Exception) {
        false
    }

    // Even a lazy bean definition links the expansion and its PlaceholderAPI superclass.
    if (installed("PlaceholderAPI")) integrationSingleton { nexusLumaGuildsExpansion() }
    if (!installed(APOLLO_PLUGIN_NAME) || !plugin.config.getBoolean("apollo.enabled", true)) return

    // Registered under the shared application contract, not the Bukkit implementation.
    integrationSingleton<LunarClientService> { LunarClientServiceBukkit() }

    // Guild Teams
    if (plugin.config.getBoolean("apollo.teams.enabled", true)) {
        integrationSingleton {
            GuildTeamService(
                plugin = getBean<Plugin>(),
                lunarClientService = getBean<LunarClientService>(),
                guildService = getBean<GuildService>(),
                memberService = getBean<MemberService>(),
                rankService = getBean<RankService>(),
            )
        }
        integrationSingleton {
            GuildTeamListener(
                guildTeamService = getBean<GuildTeamService>(),
                memberService = getBean<MemberService>(),
                // Koin getOrNull(): null when waypoints are disabled (bean never registered).
                guildWaypointService = optionalGuildWaypointService(),
            guildNotificationService = { if (containsBean("guildNotificationService")) getBean<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>() else null },
        )
        }
    }

    // Guild Waypoints
    if (plugin.config.getBoolean("apollo.waypoints.enabled", true)) {
        integrationSingleton {
            GuildWaypointService(
                plugin = getBean<Plugin>(),
                lunarClientService = getBean<LunarClientService>(),
                guildService = getBean<GuildService>(),
                memberService = getBean<MemberService>(),
            )
        }
    }

    // Guild Notifications
    if (plugin.config.getBoolean("apollo.notifications.enabled", true)) {
        integrationSingleton {
            GuildNotificationService(
                plugin = getBean<Plugin>(),
                lunarClientService = getBean<LunarClientService>(),
                guildService = getBean<GuildService>(),
                memberService = getBean<MemberService>(),
            )
        }
        integrationSingleton {
            GuildNotificationListener(
                notificationService = getBean<GuildNotificationService>(),
                guildService = getBean<GuildService>(),
                memberService = getBean<MemberService>(),
            )
        }
    }

    // Guild Rich Presence
    if (plugin.config.getBoolean("apollo.richpresence.enabled", true)) {
        integrationSingleton {
            GuildRichPresenceService(
                plugin = getBean<Plugin>(),
                lunarClientService = getBean<LunarClientService>(),
                guildService = getBean<GuildService>(),
                memberService = getBean<MemberService>(),
            )
        }
        integrationSingleton {
            GuildRichPresenceListener(richPresenceService = getBean<GuildRichPresenceService>())
        }
    }
}

/**
 * Koin `getOrNull<GuildWaypointService>()` equivalent: resolves only when the waypoint
 * definition was registered under its exact bean name; never constructs it otherwise.
 */
internal fun NexusContext.optionalGuildWaypointService(): GuildWaypointService? =
    if (containsBean(GUILD_WAYPOINT_SERVICE_BEAN)) getBean(GUILD_WAYPOINT_SERVICE_BEAN) as GuildWaypointService else null

private inline fun <reified T : Any> NexusContext.integrationSingleton(noinline factory: () -> T) {
    registerBean(integrationBeanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private fun integrationBeanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
