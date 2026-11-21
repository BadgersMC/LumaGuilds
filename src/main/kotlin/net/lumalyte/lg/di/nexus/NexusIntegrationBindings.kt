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

/**
 * Nexus replacement for the legacy Koin `integrationModule(plugin)` (Modules.kt).
 *
 * Registration-time behaviour mirrors Koin exactly:
 *  - LumaGuildsExpansion is always registered (Koin `singleOf(::LumaGuildsExpansion)`), as a
 *    lazy singleton. It is NOT registered with PlaceholderAPI here; that stays in
 *    LumaGuilds.initialisePlaceholderAPI (PlaceholderAPI presence check + `register()`).
 *  - Apollo-Bukkit presence is checked once, via `Bukkit.getPluginManager().getPlugin(...)`,
 *    with any Exception treated as "not installed" (same `catch (e: Exception)` as Koin).
 *  - `apollo.enabled` is only read when Apollo-Bukkit is present (short-circuit preserved);
 *    feature flags `apollo.teams|waypoints|notifications|richpresence.enabled` (default true)
 *    are only read when Apollo is enabled, in the legacy order.
 *  - When Apollo-Bukkit is absent or disabled, no Apollo-typed bean is registered at all, so
 *    optional lookups (`containsBean`) stay false and no Apollo class is ever constructed.
 *
 * Nothing is constructed at registration time: every service and listener is a lazy
 * singleton built on first `getBean`. Only `plugin.config` and the Bukkit plugin manager are
 * touched here, exactly as the Koin module body did.
 *
 * Logging difference (intentional): Koin logged "Apollo integration enabled/disabled" while
 * building the module because that module WAS the live graph. This Nexus foundation is inert
 * (not activated by LumaGuilds.onEnable), so logging here would falsely claim the integration
 * is live. The status line belongs to the live cutover / listener-registration step.
 *
 * Cutover blockers (not refactored in this tranche):
 *  - LumaGuildsExpansion is a KoinComponent (`by inject()` for ~15 services); resolving it
 *    from Nexus only constructs the object, its injected properties still read Koin.
 *  - GuildTeamListener reads GuildNotificationService through
 *    `org.koin.core.context.GlobalContext.get().getOrNull(...)` inside an event handler.
 *  - LumaGuilds.initialiseApollo() resolves LunarClientService and the listeners from Koin.
 */
internal fun NexusContext.registerIntegrationBindings(plugin: LumaGuilds) {
    // PlaceholderAPI expansion: lazy; PlaceholderAPI registration is left to plugin startup.
    integrationSingleton { LumaGuildsExpansion() }

    val apolloAvailable = try {
        Bukkit.getPluginManager().getPlugin(APOLLO_PLUGIN_NAME) != null
    } catch (e: Exception) {
        false
    }

    if (!apolloAvailable || !plugin.config.getBoolean("apollo.enabled", true)) return

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
