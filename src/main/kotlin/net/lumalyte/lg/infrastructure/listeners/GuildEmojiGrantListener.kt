package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.api.events.GuildCreatedEvent
import net.lumalyte.lg.api.events.GuildRenamedEvent
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
            emojiGrantService.reconcileMember(event.playerId, event.guildId)
        } catch (e: Exception) {
            logger.warn("Failed to grant emoji permission on join: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberRemoved(event: GuildMemberRemovedEvent) {
        try {
            emojiGrantService.removeMember(event.playerId, event.guildId)
        } catch (e: Exception) {
            logger.warn("Failed to revoke emoji permission on leave: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildCreated(event: GuildCreatedEvent) {
        reconcile("create") { emojiGrantService.reconcileGuild(event.guild.id) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildRenamed(event: GuildRenamedEvent) {
        reconcile("rename") { emojiGrantService.reconcileGuild(event.guild.id) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        try {
            emojiGrantService.removeGuild(event.guild.id)
        } catch (e: Exception) {
            logger.warn("Failed to revoke emoji permissions on disband: ${e.message}")
        }
    }

    private fun reconcile(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.warn("Failed to reconcile emoji permissions on guild $operation: ${e.message}")
        }
    }
}
