package net.lumalyte.lg.infrastructure.listeners.apollo

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.events.GuildMemberJoinEvent
import net.lumalyte.lg.domain.events.GuildRelationChangeEvent
import net.lumalyte.lg.infrastructure.services.apollo.GuildNotificationService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/**
 * Listens for guild events and sends Apollo notifications.
 */
class GuildNotificationListener(
    private val notificationService: GuildNotificationService,
    private val guildService: GuildService,
    private val memberService: MemberService
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildNotificationListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        try {
            val player = Bukkit.getPlayer(event.playerId)
            val playerName = player?.name ?: "Player"

            notificationService.notifyMemberJoin(event.guildId, playerName, event.playerId)
        } catch (e: Exception) {
            logger.error("Error handling member join notification: ${e.message}", e)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildRelationChange(event: GuildRelationChangeEvent) {
        try {
            notificationService.notifyRelationChange(
                event.guild1,
                event.guild2,
                event.newRelationType
            )
        } catch (e: Exception) {
            logger.error("Error handling relation change notification: ${e.message}", e)
        }
    }
}
