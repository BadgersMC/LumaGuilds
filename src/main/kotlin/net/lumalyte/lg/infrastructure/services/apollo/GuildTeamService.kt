package net.lumalyte.lg.infrastructure.services.apollo

import com.lunarclient.apollo.Apollo
import com.lunarclient.apollo.module.glow.GlowModule
import com.lunarclient.apollo.module.team.TeamMember
import com.lunarclient.apollo.module.team.TeamModule
import com.lunarclient.apollo.recipients.Recipients
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.LunarPreferenceRepository
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
    private val rankService: RankService,
    private val lunarPreferences: LunarPreferenceRepository
) {
    private val logger = LoggerFactory.getLogger(GuildTeamService::class.java)

    private val teamModule: TeamModule by lazy {
        Apollo.getModuleManager().getModule(TeamModule::class.java)
    }

    private val glowModule: GlowModule by lazy {
        Apollo.getModuleManager().getModule(GlowModule::class.java)
    }

    // Track which guilds have active teams (guildId -> set of playerIds)
    private val activeTeams = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // Cached marker colors per player — only refreshed on membership/rank changes
    private val cachedMarkerColors = ConcurrentHashMap<UUID, Color>()

    // Cached guild member lists — only refreshed on membership changes
    private val cachedGuildMembers = ConcurrentHashMap<UUID, List<UUID>>()

    // Cached guild tracking state
    private val cachedTrackingEnabled = ConcurrentHashMap<UUID, Boolean>()

    private var refreshTaskId: Int? = null

    fun start() {
        if (!lunarClientService.isApolloAvailable()) {
            logger.warn("Cannot start GuildTeamService - Apollo not available")
            return
        }

        if (!plugin.config.getBoolean("apollo.teams.enabled", true)) {
            logger.info("Guild teams module disabled in config")
            return
        }

        try {
            initializeExistingGuilds()

            val refreshRate = plugin.config.getLong("apollo.teams.refresh_rate", 1L)

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

    private fun Player.isVanished(): Boolean =
        getMetadata("vanished").any { it.asBoolean() }

    private fun Player.isTrackingOptedIn(): Boolean =
        lunarPreferences.isPlayerTeamTrackingEnabled(uniqueId)

    private fun initializeExistingGuilds() {
        val allGuilds = guildService.getAllGuilds()
        var teamCount = 0

        allGuilds.forEach { guild ->
            try {
                cachedTrackingEnabled[guild.id] = guild.trackingEnabled
                val members = memberService.getGuildMembers(guild.id)
                if (members.isNotEmpty()) {
                    cachedGuildMembers[guild.id] = members.map { it.playerId }
                    members.forEach { member ->
                        cacheMarkerColor(member.playerId, guild.id)
                    }
                    createGuildTeam(guild.id)
                    teamCount++
                }
            } catch (e: Exception) {
                logger.warn("Failed to initialize team for guild ${guild.name}: ${e.message}")
            }
        }

        logger.info("Initialized $teamCount guild teams")
    }

    private fun cacheMarkerColor(playerId: UUID, guildId: UUID) {
        val config = plugin.config
        if (!config.getBoolean("apollo.teams.rank_based_colors", true)) {
            cachedMarkerColors[playerId] = getGuildColor(guildId)
            return
        }
        try {
            val member = memberService.getMember(playerId, guildId)
            val rank = if (member != null) rankService.getRank(member.rankId) else null

            val leaderColor = parseColor(config.getString("apollo.teams.colors.leader", "255,215,0"))
            val officerColor = parseColor(config.getString("apollo.teams.colors.officer", "0,150,255"))
            val memberColor = parseColor(config.getString("apollo.teams.colors.member", "0,255,0"))

            cachedMarkerColors[playerId] = when {
                rank == null -> memberColor
                rank.priority >= 90 -> leaderColor
                rank.priority >= 50 -> officerColor
                else -> memberColor
            }
        } catch (e: Exception) {
            cachedMarkerColors[playerId] = Color.GREEN
        }
    }

    /**
     * Invalidate caches for a guild so the next event-driven call re-fetches from DB.
     * Call this on membership/rank changes — NOT on the per-tick refresh path.
     */
    fun invalidateGuildCache(guildId: UUID) {
        cachedGuildMembers.remove(guildId)
        cachedTrackingEnabled.remove(guildId)
    }

    fun createGuildTeam(guildId: UUID) {
        try {
            val trackingEnabled = cachedTrackingEnabled.getOrPut(guildId) {
                guildService.getGuild(guildId)?.trackingEnabled ?: false
            }
            if (!trackingEnabled) return

            val memberIds = cachedGuildMembers.getOrPut(guildId) {
                memberService.getGuildMembers(guildId).map { it.playerId }
            }
            if (memberIds.isEmpty()) return

            val onlineMembers = memberIds.mapNotNull { id ->
                Bukkit.getPlayer(id)?.takeUnless { it.isVanished() || !it.isTrackingOptedIn() }
            }
            if (onlineMembers.isEmpty()) return

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
                                logger.debug("Failed to create team member for ${teammate.name}: ${e.message}")
                                null
                            }
                        }

                    if (teamMembers.isNotEmpty()) {
                        teamModule.updateTeamMembers(apolloPlayer, teamMembers)
                        logger.info("Created team for LC user ${viewer.name} with ${teamMembers.size} teammates")
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to update team for viewer ${viewer.name}: ${e.message}")
                }
            }

            activeTeams[guildId] = onlineMembers.map { it.uniqueId }.toMutableSet()
            logger.info("Guild team created for $guildId with ${onlineMembers.size} online members")
        } catch (e: Exception) {
            logger.warn("Failed to create guild team for $guildId: ${e.message}")
        }
    }

    private fun createTeamMember(player: Player, guildId: UUID): TeamMember {
        val markerColor = cachedMarkerColors[player.uniqueId] ?: Color.GREEN
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

    private fun getGuildColor(guildId: UUID): Color {
        val colorString = plugin.config.getString("apollo.teams.default_guild_color", "0,255,0")
        return parseColor(colorString)
    }

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
     * Per-tick refresh — only reads cached data and player positions. Zero DB queries.
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

    fun refreshGuildTeam(guildId: UUID) {
        try {
            val trackingEnabled = cachedTrackingEnabled[guildId] ?: true
            if (!trackingEnabled) {
                deleteGuildTeam(guildId)
                return
            }

            val memberIds = cachedGuildMembers[guildId]
            if (memberIds == null) {
                // No cache — likely a new guild, do a one-time DB fetch and cache it
                val members = memberService.getGuildMembers(guildId)
                if (members.isEmpty()) {
                    deleteGuildTeam(guildId)
                    return
                }
                cachedGuildMembers[guildId] = members.map { it.playerId }
                members.forEach { cacheMarkerColor(it.playerId, guildId) }
                refreshGuildTeam(guildId)
                return
            }

            val onlineMembers = memberIds.mapNotNull { id ->
                Bukkit.getPlayer(id)?.takeUnless { it.isVanished() || !it.isTrackingOptedIn() }
            }

            if (onlineMembers.isEmpty()) {
                deleteGuildTeam(guildId)
                return
            }

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
                        teamModule.resetTeamMembers(apolloPlayer)
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to update team for viewer ${viewer.name}: ${e.message}")
                }
            }

            activeTeams[guildId] = onlineMembers.map { it.uniqueId }.toMutableSet()
        } catch (e: Exception) {
            logger.debug("Failed to refresh guild team for $guildId: ${e.message}")
        }
    }

    fun onPlayerJoinGuild(playerId: UUID, guildId: UUID) {
        try {
            invalidateGuildCache(guildId)
            cacheMarkerColor(playerId, guildId)

            val members = memberService.getGuildMembers(guildId)
            cachedGuildMembers[guildId] = members.map { it.playerId }
            cachedTrackingEnabled[guildId] = guildService.getGuild(guildId)?.trackingEnabled ?: true

            refreshGuildTeam(guildId)

            val player = Bukkit.getPlayer(playerId) ?: return
            if (!lunarClientService.isLunarClient(player)) return
            createGuildTeam(guildId)
        } catch (e: Exception) {
            logger.warn("Failed to handle player join guild: ${e.message}")
        }
    }

    fun onPlayerLeaveGuild(playerId: UUID, guildId: UUID) {
        try {
            activeTeams[guildId]?.remove(playerId)
            cachedMarkerColors.remove(playerId)
            invalidateGuildCache(guildId)

            val members = memberService.getGuildMembers(guildId)
            cachedGuildMembers[guildId] = members.map { it.playerId }

            val player = Bukkit.getPlayer(playerId)
            if (player != null && lunarClientService.isLunarClient(player)) {
                val apolloPlayer = lunarClientService.getApolloPlayer(player)
                apolloPlayer?.let {
                    teamModule.resetTeamMembers(it)
                }
            }

            refreshGuildTeam(guildId)
        } catch (e: Exception) {
            logger.warn("Failed to handle player leave guild: ${e.message}")
        }
    }

    /**
     * Called when a player toggles their team-tracking opt-in. Resets team data on the
     * toggling player's own LC client and refreshes team data for all their guilds so
     * teammates see them appear/disappear immediately.
     */
    fun onPlayerTrackingPreferenceChanged(playerId: UUID) {
        try {
            val player = Bukkit.getPlayer(playerId)
            if (player != null && lunarClientService.isLunarClient(player)) {
                lunarClientService.getApolloPlayer(player)?.let {
                    teamModule.resetTeamMembers(it)
                }
            }
            memberService.getPlayerGuilds(playerId).forEach { guildId ->
                refreshGuildTeam(guildId)
            }
        } catch (e: Exception) {
            logger.debug("Failed to handle tracking preference change for $playerId: ${e.message}")
        }
    }

    fun onPlayerQuit(playerId: UUID) {
        try {
            activeTeams.values.forEach { members ->
                members.remove(playerId)
            }

            val playerGuilds = memberService.getPlayerGuilds(playerId)
            playerGuilds.forEach { guildId ->
                refreshGuildTeam(guildId)
            }

            if (lunarClientService is LunarClientServiceBukkit) {
                lunarClientService.clearPlayerCache(playerId)
            }
        } catch (e: Exception) {
            logger.debug("Failed to handle player quit: ${e.message}")
        }
    }

    fun deleteGuildTeam(guildId: UUID) {
        try {
            val members = activeTeams.remove(guildId) ?: return

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

    fun stop() {
        try {
            refreshTaskId?.let { Bukkit.getScheduler().cancelTask(it) }

            activeTeams.keys.toList().forEach { guildId ->
                deleteGuildTeam(guildId)
            }

            cachedMarkerColors.clear()
            cachedGuildMembers.clear()
            cachedTrackingEnabled.clear()

            logger.info("Guild team service stopped")
        } catch (e: Exception) {
            logger.error("Error stopping guild team service: ${e.message}", e)
        }
    }

    fun enableGuildGlow(guildId: UUID) {
        if (!plugin.config.getBoolean("apollo.teams.glow.enabled", false)) {
            return
        }

        try {
            val memberIds = cachedGuildMembers.getOrPut(guildId) {
                memberService.getGuildMembers(guildId).map { it.playerId }
            }
            val onlineMembers = memberIds.mapNotNull {
                Bukkit.getPlayer(it)?.takeUnless { p -> p.isVanished() || !p.isTrackingOptedIn() }
            }

            onlineMembers.forEach { viewer ->
                if (!lunarClientService.isLunarClient(viewer)) return@forEach

                onlineMembers.forEach { target ->
                    if (target.uniqueId != viewer.uniqueId) {
                        try {
                            val color = cachedMarkerColors[target.uniqueId] ?: Color.GREEN
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

    fun disableGuildGlow(guildId: UUID) {
        try {
            val memberIds = cachedGuildMembers[guildId] ?: return
            val onlineMembers = memberIds.mapNotNull { Bukkit.getPlayer(it) }

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

    fun getStats(): Map<String, Any> {
        return mapOf(
            "active_guilds" to activeTeams.size,
            "total_tracked_players" to activeTeams.values.sumOf { it.size },
            "refresh_rate_ms" to (plugin.config.getLong("apollo.teams.refresh_rate", 1L) * 50)
        )
    }
}
