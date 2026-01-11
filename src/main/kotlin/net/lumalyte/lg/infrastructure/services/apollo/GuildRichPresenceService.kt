package net.lumalyte.lg.infrastructure.services.apollo

import com.lunarclient.apollo.Apollo
import com.lunarclient.apollo.module.richpresence.RichPresenceModule
import com.lunarclient.apollo.module.richpresence.ServerRichPresence
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.apollo.LunarClientService
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/**
 * Manages Apollo Rich Presence integration for guilds.
 * Shows guild information on Discord and Lunar Client launcher.
 */
class GuildRichPresenceService(
    private val plugin: Plugin,
    private val lunarClientService: LunarClientService,
    private val guildService: GuildService,
    private val memberService: MemberService
) {
    private val logger = LoggerFactory.getLogger(GuildRichPresenceService::class.java)

    // Apollo rich presence module
    private val richPresenceModule: RichPresenceModule by lazy {
        Apollo.getModuleManager().getModule(RichPresenceModule::class.java)
    }

    /**
     * Update a player's rich presence to show their guild information.
     * Displays on Discord and Lunar Client launcher.
     */
    fun updateGuildRichPresence(player: Player) {
        if (!plugin.config.getBoolean("apollo.richpresence.enabled", true)) return
        if (!lunarClientService.isLunarClient(player)) return

        try {
            val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
            val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)

            if (playerGuilds.isEmpty()) {
                // Player has no guild - reset to default
                resetRichPresence(player)
                return
            }

            // Get player's primary guild (first one)
            val guildId = playerGuilds.first()
            val guild = guildService.getGuild(guildId) ?: return
            val members = memberService.getGuildMembers(guildId)
            val onlineMembers = members.count { member ->
                org.bukkit.Bukkit.getPlayer(member.playerId) != null
            }

            // Get server IP from config
            val serverIp = plugin.config.getString("apollo.richpresence.server_ip", "")

            // Build rich presence with guild info
            val builder = ServerRichPresence.builder()
                .gameName("Guild: ${guild.name}")
                .gameVariantName(guild.tag ?: "No Tag")
                .gameState("Online")
                .playerState("Playing")
                .teamCurrentSize(onlineMembers)
                .teamMaxSize(plugin.config.getInt("guild.max_members_per_guild", 50))

            // Add server IP if configured
            if (!serverIp.isNullOrBlank()) {
                builder.subServerName(serverIp)
            }

            val richPresence = builder.build()

            richPresenceModule.overrideServerRichPresence(apolloPlayer, richPresence)
            logger.debug("Updated rich presence for ${player.name}: Guild ${guild.name}")
        } catch (e: Exception) {
            logger.debug("Failed to update rich presence for ${player.name}: ${e.message}")
        }
    }

    /**
     * Reset a player's rich presence to server default.
     */
    fun resetRichPresence(player: Player) {
        if (!lunarClientService.isLunarClient(player)) return

        try {
            val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
            richPresenceModule.resetServerRichPresence(apolloPlayer)
            logger.debug("Reset rich presence for ${player.name}")
        } catch (e: Exception) {
            logger.debug("Failed to reset rich presence for ${player.name}: ${e.message}")
        }
    }

    /**
     * Handle player joining a guild - update their presence.
     */
    fun onPlayerJoinGuild(player: Player) {
        updateGuildRichPresence(player)
    }

    /**
     * Handle player leaving a guild - reset their presence.
     */
    fun onPlayerLeaveGuild(player: Player) {
        val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)
        if (playerGuilds.isEmpty()) {
            resetRichPresence(player)
        } else {
            // Still in other guilds, update to show new primary guild
            updateGuildRichPresence(player)
        }
    }

    /**
     * Update rich presence for all online members of a guild.
     * Useful when guild info changes (name, tag, etc.)
     */
    fun updateGuildMembersPresence(guildId: java.util.UUID) {
        try {
            val members = memberService.getGuildMembers(guildId)
            members.forEach { member ->
                val player = org.bukkit.Bukkit.getPlayer(member.playerId)
                if (player != null && lunarClientService.isLunarClient(player)) {
                    updateGuildRichPresence(player)
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to update guild members presence for $guildId: ${e.message}")
        }
    }
}
