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
import net.lumalyte.lg.application.services.EmojiGrantReconciliationResult

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
        reconcile("member join player=${event.playerId} guild=${event.guildId}") {
            emojiGrantService.reconcileMember(event.playerId, event.guildId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberRemoved(event: GuildMemberRemovedEvent) {
        reconcile("member removal player=${event.playerId} guild=${event.guildId}") {
            emojiGrantService.removeMember(event.playerId, event.guildId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildCreated(event: GuildCreatedEvent) {
        reconcile("guild create guild=${event.guild.id}") { emojiGrantService.reconcileGuild(event.guild.id) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildRenamed(event: GuildRenamedEvent) {
        reconcile("guild rename guild=${event.guildId}") { emojiGrantService.reconcileGuild(event.guildId) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        reconcile("guild disband guild=${event.guild.id}") { emojiGrantService.removeGuild(event.guild.id) }
    }

    private fun reconcile(operation: String, action: () -> EmojiGrantReconciliationResult) {
        try {
            val result = action()
            if (!result.successful) {
                logger.warn("Emoji permission reconciliation failed for $operation: ${result.failed} operation(s) will retry on global reconciliation")
            }
        } catch (e: Exception) {
            logger.warn("Failed to reconcile emoji permissions for $operation: ${e.message}")
        }
    }
}
