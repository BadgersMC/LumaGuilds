package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.domain.events.GuildMemberRemovedEvent
import net.lumalyte.lg.domain.events.GuildRelationChangeEvent
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Listener to handle RoseChat channel cleanup when players leave, guilds are disbanded,
 * or relationships (allies) change. Ensures players are removed from guild-specific 
 * and ally-specific channels to prevent "ghost" memberships and resonance.
 */
class RoseChatCleanupListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberRemoved(event: GuildMemberRemovedEvent) {
        val player = Bukkit.getPlayer(event.playerId) ?: return
        cleanupPlayer(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        event.memberIds.forEach { memberId ->
            cleanupPlayer(memberId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRelationChange(event: GuildRelationChangeEvent) {
        // If an alliance is broken, we need to boot members from any shared ally channels
        if (event.type == RelationType.NEUTRAL || event.type == RelationType.ENEMY) {
            // Note: In LumaGuilds, relation changes often trigger specific logic.
            // Here we proactively clean participants if they were in an ally-related channel.
            event.sourceGuild.memberIds.forEach { cleanupPlayer(it) }
            event.targetGuild.memberIds.forEach { cleanupPlayer(it) }
        }
    }

    private fun cleanupPlayer(playerId: java.util.UUID) {
        try {
            val player = Bukkit.getPlayer(playerId) ?: return
            if (!player.isOnline) return
            
            val rosePlayer = RosePlayer(player)
            val api = RoseChatAPI.getInstance()
            val currentChannel = rosePlayer.playerData?.currentChannel ?: return

            // Check if the channel is guild-related or ally-related
            // Matches 'guild_', 'ally_', or 'officer_' patterns used by Luma/RoseChat hooks
            val channelId = currentChannel.id.lowercase()
            if (channelId.contains("guild") || channelId.contains("ally") || channelId.contains("officer")) {
                val defaultChannel = api.channelManager.defaultChannel
                if (defaultChannel != null && currentChannel.id != defaultChannel.id) {
                    rosePlayer.switchChannel(defaultChannel)
                }
            }
        } catch (e: NoClassDefFoundError) {
            // RoseChat not loaded
        } catch (e: Exception) {
            // Log or ignore
        }
    }
}