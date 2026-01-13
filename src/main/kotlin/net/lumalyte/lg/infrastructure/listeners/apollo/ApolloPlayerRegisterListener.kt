package net.lumalyte.lg.infrastructure.listeners.apollo

import com.lunarclient.apollo.event.ApolloListener
import com.lunarclient.apollo.event.Listen
import com.lunarclient.apollo.event.player.ApolloRegisterPlayerEvent
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService
import net.lumalyte.lg.infrastructure.services.apollo.GuildRichPresenceService
import net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService
import net.lumalyte.lg.infrastructure.services.apollo.GuildWaypointService
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory

/**
 * Listens for Apollo player registration events.
 * This event fires AFTER the Apollo handshake completes successfully.
 */
class ApolloPlayerRegisterListener(
    private val guildTeamService: GuildTeamService,
    private val memberService: MemberService,
    private val guildWaypointService: GuildWaypointService?,
    private val guildNotificationService: GuildNotificationService?,
    private val guildRichPresenceService: GuildRichPresenceService?
) : ApolloListener {

    private val logger = LoggerFactory.getLogger(ApolloPlayerRegisterListener::class.java)

    /**
     * Called when a Lunar Client player completes the Apollo handshake.
     * This is the CORRECT place to send notifications and update Apollo features.
     */
    @Listen
    fun onApolloRegister(event: ApolloRegisterPlayerEvent) {
        try {
            val player = event.player.player as? Player ?: return
            logger.info("Apollo player registered: ${player.name} (UUID: ${player.uniqueId})")

            val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)

            // Refresh teams for player's guilds
            playerGuilds.forEach { guildId ->
                guildTeamService.refreshGuildTeam(guildId)
            }

            // Show waypoints for guild homes
            guildWaypointService?.onPlayerJoin(player)

            // Send welcome notification (no delay needed - Apollo is ready!)
            guildNotificationService?.sendWelcomeNotification(player)

            // Update rich presence with guild info
            guildRichPresenceService?.updateGuildRichPresence(player)

            logger.info("Successfully initialized Apollo features for ${player.name}")
        } catch (e: Exception) {
            logger.error("Error handling Apollo player registration: ${e.message}", e)
        }
    }
}
