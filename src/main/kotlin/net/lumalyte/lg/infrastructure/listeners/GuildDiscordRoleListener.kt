package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.api.events.GuildCreatedEvent
import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.api.events.GuildRenamedEvent
import net.lumalyte.lg.application.services.DiscordGuildRoleSyncSummary
import net.lumalyte.lg.application.services.GuildDiscordRoleService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class GuildDiscordRoleListener(
    private val service: GuildDiscordRoleService,
) : Listener {
    private val logger = LoggerFactory.getLogger(GuildDiscordRoleListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onMemberJoin(event: GuildMemberJoinEvent) = observe(
        "member join player=${event.playerId} guild=${event.guildId}",
        service.memberJoined(event.guildId, event.playerId),
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onMemberRemoved(event: GuildMemberRemovedEvent) = observe(
        "member removal player=${event.playerId} guild=${event.guildId}",
        service.memberRemoved(event.guildId, event.playerId),
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildCreated(event: GuildCreatedEvent) = observe(
        "guild create guild=${event.guild.id}",
        service.reconcileGuild(event.guild.id),
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildRenamed(event: GuildRenamedEvent) = observe(
        "guild rename guild=${event.guildId}",
        service.guildRenamed(event.guildId),
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) = observe(
        "guild disband guild=${event.guild.id}",
        service.guildDisbanded(event.guild.id),
    )

    private fun observe(operation: String, future: CompletableFuture<DiscordGuildRoleSyncSummary>) {
        future.whenComplete { result, error ->
            if (error != null) {
                logger.warn("Discord guild-role sync failed for $operation", error.cause ?: error)
            } else if (result.failures > 0) {
                logger.warn("Discord guild-role sync completed with ${result.failures} failure(s) for $operation")
            }
        }
    }
}
