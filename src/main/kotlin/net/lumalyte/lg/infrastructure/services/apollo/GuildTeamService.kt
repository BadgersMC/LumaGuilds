package net.lumalyte.lg.infrastructure.services.apollo

import com.lunarclient.apollo.Apollo
import com.lunarclient.apollo.module.glow.GlowModule
import com.lunarclient.apollo.module.team.TeamMember
import com.lunarclient.apollo.module.team.TeamModule
import com.lunarclient.apollo.recipients.Recipients
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Apollo team integration for guilds.
 * Shows guild members on Lunar Client minimap, direction HUD, and overhead markers.
 */
class GuildTeamService(
    private val plugin: Plugin,
    private val lunarClientService: LunarClientService,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val rankService: RankService
) {
    private val logger = LoggerFactory.getLogger(GuildTeamService::class.java)

    // Apollo modules
    private val teamModule: TeamModule by lazy {
        Apollo.getModuleManager().getModule(TeamModule::class.java)
    }

    private val glowModule: GlowModule by lazy {
        Apollo.getModuleManager().getModule(GlowModule::class.java)
    }

    // Track which guilds have active teams (guildId -> set of playerIds)
    private val activeTeams = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // Refresh task for updating team member locations
    private var refreshTaskId: Int? = null

    /**
     * Initialize the team service and start refresh task
     */
    fun start() {
        if (!lunarClientService.isApolloAvailable()) {
            logger.warn("Cannot start GuildTeamService - Apollo not available")
            return
        }

        // Check if teams module is enabled
        if (!plugin.config.getBoolean("apollo.teams.enabled", true)) {
            logger.info("Guild teams module disabled in config")
            return
        }

        try {
            // Create teams for all existing guilds
            initializeExistingGuilds()

            // Get refresh rate from config (default: 1 tick = 50ms)
            val refreshRate = plugin.config.getLong("apollo.teams.refresh_rate", 1L)

            // Start refresh task
            refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
                try {
                    refreshAllTeams()
                } catch (e: Exception) {
                    logger.error("Error refreshing teams: ${e.message}", e)
                }
            }, refreshRate, refreshRate)

            logger.info("Guild team service started - teams refresh every ${refreshRate * 50}ms")
        } catch (e: Exception) {
            logger.error("Failed to start GuildTeamService: ${e.message}", e)
        }
    }

    /**
     * Create Apollo teams for all existing guilds on startup
     */
    private fun initializeExistingGuilds() {
        val allGuilds = guildService.getAllGuilds()
        var teamCount = 0

        allGuilds.forEach { guild ->
            try {
                val members = memberService.getGuildMembers(guild.id)
                if (members.isNotEmpty()) {
                    createGuildTeam(guild.id)
                    teamCount++
                }
            } catch (e: Exception) {
                logger.warn("Failed to initialize team for guild ${guild.name}: ${e.message}")
            }
        }

        logger.info("Initialized $teamCount guild teams")
    }

    /**
     * Create an Apollo team for a guild
     */
    fun createGuildTeam(guildId: UUID) {
        try {
            val members = memberService.getGuildMembers(guildId)
            if (members.isEmpty()) return

            val onlineMembers = members.mapNotNull { member ->
                Bukkit.getPlayer(member.playerId)
            }

            if (onlineMembers.isEmpty()) return

            // Show this team to all Lunar Client players in the guild
            onlineMembers.forEach { viewer ->
                try {
                    if (!lunarClientService.isLunarClient(viewer)) return@forEach
                    val apolloPlayer = lunarClientService.getApolloPlayer(viewer) ?: return@forEach

                    // Create team members list (excluding self)
                    val teamMembers = onlineMembers
                        .filter { it.uniqueId != viewer.uniqueId }
                        .mapNotNull { teammate ->
                            try {
                                createTeamMember(teammate, guildId)
                            } catch (e: Exception) {
                                logger.debug("Failed to create team member for ${teammate.name}: ${e.message}")
                                null
                            }
                        }

                    if (teamMembers.isNotEmpty()) {
                        teamModule.updateTeamMembers(apolloPlayer, teamMembers)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to update team for viewer ${viewer.name}: ${e.message}")
                }
            }

            // Track this guild as having an active team
            activeTeams[guildId] = onlineMembers.map { it.uniqueId }.toMutableSet()
        } catch (e: Exception) {
            logger.warn("Failed to create guild team for $guildId: ${e.message}")
        }
    }

    /**
     * Create a team member representation for Apollo
     */
    private fun createTeamMember(player: Player, guildId: UUID): TeamMember {
        val markerColor = getMarkerColor(player, guildId)
        val location = player.location

        return TeamMember.builder()
            .playerUuid(player.uniqueId)
            .displayName(Component.text(player.name))
            .markerColor(markerColor)
            .location(com.lunarclient.apollo.common.location.ApolloLocation.builder()
                .world(location.world.name)
                .x(location.x)
                .y(location.y)
                .z(location.z)
                .build()
            )
            .build()
    }

    /**
     * Get the marker color for a player based on their rank
     */
    private fun getMarkerColor(player: Player, guildId: UUID): Color {
        val config = plugin.config

        // Check if rank-based colors are enabled
        if (!config.getBoolean("apollo.teams.rank_based_colors", true)) {
            // Use guild default color
            return getGuildColor(guildId)
        }

        try {
            // Get player's rank
            val member = memberService.getMember(player.uniqueId, guildId) ?: return Color.GREEN
            val rank = rankService.getRank(member.rankId) ?: return Color.GREEN

            // Parse colors from config
            val leaderColor = parseColor(config.getString("apollo.teams.colors.leader", "255,215,0"))
            val officerColor = parseColor(config.getString("apollo.teams.colors.officer", "0,150,255"))
            val memberColor = parseColor(config.getString("apollo.teams.colors.member", "0,255,0"))

            // Color by rank priority
            return when {
                rank.priority >= 90 -> leaderColor  // Guild Leader
                rank.priority >= 50 -> officerColor // Officer
                else -> memberColor                  // Member
            }
        } catch (e: Exception) {
            logger.debug("Failed to get marker color for ${player.name}: ${e.message}")
            return Color.GREEN
        }
    }

    /**
     * Get guild's team color from config
     */
    private fun getGuildColor(guildId: UUID): Color {
        val colorString = plugin.config.getString("apollo.teams.default_guild_color", "0,255,0")
        return parseColor(colorString)
    }

    /**
     * Parse color from "R,G,B" format
     */
    private fun parseColor(colorString: String?): Color {
        return try {
            val parts = colorString?.split(",")?.map { it.trim().toInt() }
            if (parts != null && parts.size == 3) {
                Color(parts[0], parts[1], parts[2])
            } else {
                Color.GREEN // Default fallback
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse color '$colorString': ${e.message}")
            Color.GREEN
        }
    }

    /**
     * Refresh all team locations (called every tick by default)
     */
    private fun refreshAllTeams() {
        activeTeams.keys.toList().forEach { guildId ->
            try {
                refreshGuildTeam(guildId)
            } catch (e: Exception) {
                logger.debug("Failed to refresh team for guild $guildId: ${e.message}")
            }
        }
    }

    /**
     * Refresh a specific guild's team
     */
    fun refreshGuildTeam(guildId: UUID) {
        try {
            val members = memberService.getGuildMembers(guildId)
            val onlineMembers = members.mapNotNull { member ->
                Bukkit.getPlayer(member.playerId)
            }

            if (onlineMembers.isEmpty()) {
                // No online members - remove team
                deleteGuildTeam(guildId)
                return
            }

            // Update each viewer's team view
            onlineMembers.forEach { viewer ->
                try {
                    if (!lunarClientService.isLunarClient(viewer)) return@forEach
                    val apolloPlayer = lunarClientService.getApolloPlayer(viewer) ?: return@forEach

                    val teamMembers = onlineMembers
                        .filter { it.uniqueId != viewer.uniqueId }
                        .mapNotNull { teammate ->
                            try {
                                createTeamMember(teammate, guildId)
                            } catch (e: Exception) {
                                null
                            }
                        }

                    if (teamMembers.isNotEmpty()) {
                        teamModule.updateTeamMembers(apolloPlayer, teamMembers)
                    } else {
                        // No teammates - clear team view
                        teamModule.resetTeamMembers(apolloPlayer)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to update team for viewer ${viewer.name}: ${e.message}")
                }
            }

            // Update tracked members
            activeTeams[guildId] = onlineMembers.map { it.uniqueId }.toMutableSet()
        } catch (e: Exception) {
            logger.debug("Failed to refresh guild team for $guildId: ${e.message}")
        }
    }

    /**
     * Handle player joining a guild
     */
    fun onPlayerJoinGuild(playerId: UUID, guildId: UUID) {
        try {
            // Refresh the guild's team to include new member
            refreshGuildTeam(guildId)

            // If player is on LC, show them their team
            val player = Bukkit.getPlayer(playerId) ?: return
            if (!lunarClientService.isLunarClient(player)) return

            createGuildTeam(guildId)
        } catch (e: Exception) {
            logger.warn("Failed to handle player join guild: ${e.message}")
        }
    }

    /**
     * Handle player leaving a guild
     */
    fun onPlayerLeaveGuild(playerId: UUID, guildId: UUID) {
        try {
            // Remove player from tracked members
            activeTeams[guildId]?.remove(playerId)

            // Clear team for leaving player
            val player = Bukkit.getPlayer(playerId)
            if (player != null && lunarClientService.isLunarClient(player)) {
                val apolloPlayer = lunarClientService.getApolloPlayer(player)
                apolloPlayer?.let {
                    teamModule.resetTeamMembers(it)
                }
            }

            // Refresh team for remaining members
            refreshGuildTeam(guildId)
        } catch (e: Exception) {
            logger.warn("Failed to handle player leave guild: ${e.message}")
        }
    }

    /**
     * Handle player disconnect
     */
    fun onPlayerQuit(playerId: UUID) {
        try {
            // Remove from all tracked teams
            activeTeams.values.forEach { members ->
                members.remove(playerId)
            }

            // Refresh all teams this player was in
            val playerGuilds = memberService.getPlayerGuilds(playerId)
            playerGuilds.forEach { guildId ->
                refreshGuildTeam(guildId)
            }

            // Clear player from LC service cache
            if (lunarClientService is LunarClientServiceBukkit) {
                lunarClientService.clearPlayerCache(playerId)
            }
        } catch (e: Exception) {
            logger.debug("Failed to handle player quit: ${e.message}")
        }
    }

    /**
     * Delete a guild's team
     */
    fun deleteGuildTeam(guildId: UUID) {
        try {
            val members = activeTeams.remove(guildId) ?: return

            // Clear teams for all members
            members.forEach { memberId ->
                try {
                    val player = Bukkit.getPlayer(memberId)
                    if (player != null && lunarClientService.isLunarClient(player)) {
                        val apolloPlayer = lunarClientService.getApolloPlayer(player)
                        apolloPlayer?.let {
                            teamModule.resetTeamMembers(it)
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to clear team for member $memberId: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to delete guild team: ${e.message}")
        }
    }

    /**
     * Stop the team service and cleanup
     */
    fun stop() {
        try {
            refreshTaskId?.let { Bukkit.getScheduler().cancelTask(it) }

            // Clear all teams
            activeTeams.keys.toList().forEach { guildId ->
                deleteGuildTeam(guildId)
            }

            logger.info("Guild team service stopped")
        } catch (e: Exception) {
            logger.error("Error stopping guild team service: ${e.message}", e)
        }
    }

    /**
     * Apply glow effect to guild members (optional feature)
     */
    fun enableGuildGlow(guildId: UUID) {
        if (!plugin.config.getBoolean("apollo.teams.glow.enabled", false)) {
            return
        }

        try {
            val members = memberService.getGuildMembers(guildId)
            val onlineMembers = members.mapNotNull { Bukkit.getPlayer(it.playerId) }

            onlineMembers.forEach { viewer ->
                if (!lunarClientService.isLunarClient(viewer)) return@forEach

                // Make other guild members glow for this viewer
                onlineMembers.forEach { target ->
                    if (target.uniqueId != viewer.uniqueId) {
                        try {
                            val color = getMarkerColor(target, guildId)
                            val apolloViewer = lunarClientService.getApolloPlayer(viewer)
                            apolloViewer?.let {
                                glowModule.overrideGlow(it, target.uniqueId, color)
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to apply glow: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to enable guild glow: ${e.message}")
        }
    }

    /**
     * Disable glow effects for a guild
     */
    fun disableGuildGlow(guildId: UUID) {
        try {
            val members = memberService.getGuildMembers(guildId)
            val onlineMembers = members.mapNotNull { Bukkit.getPlayer(it.playerId) }

            onlineMembers.forEach { viewer ->
                if (!lunarClientService.isLunarClient(viewer)) return@forEach

                onlineMembers.forEach { target ->
                    if (target.uniqueId != viewer.uniqueId) {
                        try {
                            val apolloViewer = lunarClientService.getApolloPlayer(viewer)
                            apolloViewer?.let {
                                glowModule.resetGlow(it, target.uniqueId)
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to reset glow: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to disable guild glow: ${e.message}")
        }
    }

    /**
     * Get statistics about active teams
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "active_guilds" to activeTeams.size,
            "total_tracked_players" to activeTeams.values.sumOf { it.size },
            "refresh_rate_ms" to (plugin.config.getLong("apollo.teams.refresh_rate", 1L) * 50)
        )
    }
}
