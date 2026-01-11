package net.lumalyte.lg.infrastructure.services.apollo

import com.lunarclient.apollo.Apollo
import com.lunarclient.apollo.module.waypoint.Waypoint
import com.lunarclient.apollo.module.waypoint.WaypointModule
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Apollo waypoint integration for guild homes.
 * Shows guild home locations as persistent waypoints on Lunar Client minimap.
 * Waypoints are always visible unless player toggles them off.
 */
class GuildWaypointService(
    private val plugin: Plugin,
    private val lunarClientService: LunarClientService,
    private val guildService: GuildService,
    private val memberService: MemberService
) {
    private val logger = LoggerFactory.getLogger(GuildWaypointService::class.java)

    // Apollo waypoint module
    private val waypointModule: WaypointModule by lazy {
        Apollo.getModuleManager().getModule(WaypointModule::class.java)
    }

    // Track which waypoints are active for each player (playerId -> set of waypointNames)
    private val activeWaypoints = ConcurrentHashMap<UUID, MutableSet<String>>()

    // Track guild waypoint mapping (guildId -> set of waypoint display names)
    private val guildWaypointMapping = ConcurrentHashMap<UUID, MutableSet<String>>()

    /**
     * Show all guild home waypoints for a player.
     * This is called on player join and whenever guild homes change.
     * Displays waypoints for all guilds the player is in.
     */
    fun showGuildHomeWaypoints(player: Player) {
        if (!isWaypointsEnabled()) return
        if (!lunarClientService.isLunarClient(player)) return

        try {
            val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
            val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)

            // Clear existing waypoints first
            clearWaypoints(player)

            // Add waypoints for each guild the player is in
            playerGuilds.forEach { guildId ->
                try {
                    val guild = guildService.getGuild(guildId) ?: return@forEach
                    val homes = guildService.getHomes(guildId)

                    if (!homes.hasHomes()) return@forEach

                    // Add waypoint for each home
                    homes.homes.forEach { (homeName, home) ->
                        try {
                            val world = Bukkit.getWorld(home.worldId) ?: return@forEach

                            // Create display name for the waypoint
                            val displayName = if (homeName == "main") {
                                "${guild.name} (Home)"
                            } else {
                                "${guild.name} ($homeName)"
                            }

                            // Use internal ID for tracking/removal
                            val waypointId = "guild_home_${guildId}_${homeName}"

                            val waypoint = Waypoint.builder()
                                .name(displayName)
                                .location(
                                    com.lunarclient.apollo.common.location.ApolloBlockLocation.builder()
                                        .world(world.name)
                                        .x(home.position.x)
                                        .y(home.position.y)
                                        .z(home.position.z)
                                        .build()
                                )
                                .color(getWaypointColor(player, guildId))
                                .hidden(false)
                                .build()

                            waypointModule.displayWaypoint(apolloPlayer, waypoint)

                            // Track this waypoint using display name (Apollo uses name as ID)
                            activeWaypoints.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }
                                .add(displayName)

                            // Track guild mapping for cleanup
                            guildWaypointMapping.computeIfAbsent(guildId) { ConcurrentHashMap.newKeySet() }
                                .add(displayName)

                            logger.debug("Displayed waypoint '$displayName' for ${player.name}")
                        } catch (e: Exception) {
                            logger.debug("Failed to display waypoint for home '$homeName': ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to process guild $guildId waypoints: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to show guild home waypoints for ${player.name}: ${e.message}")
        }
    }

    /**
     * Refresh waypoints for a specific guild (when homes are added/removed).
     * Updates waypoints for all online members of that guild.
     */
    fun refreshGuildWaypoints(guildId: UUID) {
        try {
            val members = memberService.getGuildMembers(guildId)
            val onlineMembers = members.mapNotNull { member ->
                Bukkit.getPlayer(member.playerId)
            }.filter { lunarClientService.isLunarClient(it) }

            onlineMembers.forEach { player ->
                showGuildHomeWaypoints(player)
            }
        } catch (e: Exception) {
            logger.debug("Failed to refresh guild waypoints: ${e.message}")
        }
    }

    /**
     * Clear all waypoints for a player (internal use).
     */
    private fun clearWaypoints(player: Player) {
        if (!lunarClientService.isLunarClient(player)) return

        try {
            val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
            val waypoints = activeWaypoints.remove(player.uniqueId) ?: return

            waypoints.forEach { waypointName ->
                try {
                    waypointModule.removeWaypoint(apolloPlayer, waypointName)
                } catch (e: Exception) {
                    logger.debug("Failed to remove waypoint '$waypointName': ${e.message}")
                }
            }

            logger.debug("Cleared ${waypoints.size} waypoints for ${player.name}")
        } catch (e: Exception) {
            logger.debug("Failed to clear waypoints for ${player.name}: ${e.message}")
        }
    }

    /**
     * Clear waypoints for a specific guild (when guild is deleted or home is removed).
     */
    fun clearGuildWaypoints(guildId: UUID) {
        try {
            // Get all waypoint names for this guild
            val guildWaypoints = guildWaypointMapping.remove(guildId) ?: return

            activeWaypoints.forEach { (playerId, playerWaypoints) ->
                val player = Bukkit.getPlayer(playerId) ?: return@forEach
                if (!lunarClientService.isLunarClient(player)) return@forEach

                val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return@forEach

                // Remove waypoints that belong to this guild
                guildWaypoints.forEach { waypointName ->
                    if (playerWaypoints.contains(waypointName)) {
                        try {
                            waypointModule.removeWaypoint(apolloPlayer, waypointName)
                            playerWaypoints.remove(waypointName)
                        } catch (e: Exception) {
                            logger.debug("Failed to remove waypoint '$waypointName': ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to clear guild waypoints: ${e.message}")
        }
    }

    /**
     * Handle player quit - cleanup waypoints.
     */
    fun onPlayerQuit(playerId: UUID) {
        activeWaypoints.remove(playerId)
    }

    /**
     * Handle player join - show waypoints.
     */
    fun onPlayerJoin(player: Player) {
        if (!isWaypointsEnabled()) return
        if (!lunarClientService.isLunarClient(player)) return

        // Delay slightly to ensure Apollo is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            showGuildHomeWaypoints(player)
        }, 20L) // 1 second delay
    }

    /**
     * Get waypoint color based on guild relationship.
     * This uses the same color scheme as the teams module for consistency.
     */
    private fun getWaypointColor(player: Player, guildId: UUID): Color {
        val config = plugin.config

        try {
            // Check if player is in this guild
            val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)

            if (playerGuilds.contains(guildId)) {
                // Own guild - use green
                val colorString = config.getString("apollo.waypoints.colors.own_guild", "0,255,0")
                return parseColor(colorString)
            }

            // For now, all other guilds use neutral color
            // Future: Could check guild relationships (allied, enemy) here
            val colorString = config.getString("apollo.waypoints.colors.neutral_guild", "255,255,0")
            return parseColor(colorString)
        } catch (e: Exception) {
            logger.debug("Failed to get waypoint color: ${e.message}")
            return Color.GREEN
        }
    }

    /**
     * Parse color from "R,G,B" format.
     */
    private fun parseColor(colorString: String?): Color {
        return try {
            val parts = colorString?.split(",")?.map { it.trim().toInt() }
            if (parts != null && parts.size == 3) {
                Color(parts[0], parts[1], parts[2])
            } else {
                Color.GREEN
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse color '$colorString': ${e.message}")
            Color.GREEN
        }
    }

    /**
     * Check if waypoints module is enabled in config.
     */
    private fun isWaypointsEnabled(): Boolean {
        return plugin.config.getBoolean("apollo.enabled", true) &&
               plugin.config.getBoolean("apollo.waypoints.enabled", true) &&
               plugin.config.getBoolean("apollo.waypoints.show_guild_homes", true) &&
               lunarClientService.isApolloAvailable()
    }

    /**
     * Get statistics about active waypoints.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "active_players" to activeWaypoints.size,
            "total_waypoints" to activeWaypoints.values.sumOf { it.size }
        )
    }
}
