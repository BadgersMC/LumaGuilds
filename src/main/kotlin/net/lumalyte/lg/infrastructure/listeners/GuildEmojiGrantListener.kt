package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.api.events.GuildDisbandedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/**
 * Listens for guild membership changes and grants/revokes emoji permissions
 * according to the [guild.emoji_grants] config.
 */
class GuildEmojiGrantListener(
    private val emojiGrantService: GuildEmojiGrantService
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildEmojiGrantListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        try {
            emojiGrantService.grantForPlayer(event.playerId, event.guildId)
        } catch (e: Exception) {
            logger.warn("Failed to grant emoji permission on join: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberRemoved(event: GuildMemberRemovedEvent) {
        try {
            emojiGrantService.revokeForPlayer(event.playerId, event.guildId)
        } catch (e: Exception) {
            logger.warn("Failed to revoke emoji permission on leave: ${e.message}")
        }
    }

    /**
     * Revokes emoji permissions for all former members when a guild is disbanded.
     * Resolves the permission from the disanded guild's name because the guild
     * record is already deleted from the repository by the time this fires.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        try {
            val permission = emojiGrantService.resolveEmojiGrantByName(event.guild.name)
            if (permission != null) {
                for (memberId in event.memberIds) {
                    emojiGrantService.revokePermission(memberId, permission)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to revoke emoji permissions on disband: ${e.message}")
        }
    }
}