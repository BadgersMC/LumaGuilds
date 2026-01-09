package net.lumalyte.lg.infrastructure.listeners.apollo

import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService
import net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.slf4j.LoggerFactory

/**
 * Listens for player join/quit events to manage Apollo team memberships and waypoints.
 */
class GuildTeamListener(
    private val guildTeamService: GuildTeamService,
    private val memberService: MemberService,
    private val guildWaypointService: GuildWaypointService? = null
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildTeamListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        try {
            val player = event.player
            val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)

            // Create/refresh teams for player's guilds
            playerGuilds.forEach { guildId ->
                guildTeamService.refreshGuildTeam(guildId)
            }

            // Show waypoints for guild homes
            guildWaypointService?.onPlayerJoin(player)

            // Send welcome notification to Lunar Client users
            val notificationService = org.koin.core.context.GlobalContext.get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService>()
            notificationService?.sendWelcomeNotification(player)
        } catch (e: Exception) {
            logger.error("Error handling player join for Apollo services: ${e.message}", e)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        try {
            val playerId = event.player.uniqueId

            // Cleanup teams
            guildTeamService.onPlayerQuit(playerId)

            // Cleanup waypoints
            guildWaypointService?.onPlayerQuit(playerId)
        } catch (e: Exception) {
            logger.error("Error handling player quit for Apollo services: ${e.message}", e)
        }
    }
}
