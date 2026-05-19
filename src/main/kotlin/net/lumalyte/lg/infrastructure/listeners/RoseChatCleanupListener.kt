package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.domain.events.GuildMemberRemovedEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Listener to handle RoseChat channel cleanup when players leave or guilds are disbanded.
 * Ensures players are removed from guild-specific channels to prevent "ghost" memberships.
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

    private fun cleanupPlayer(playerId: java.util.UUID) {
        try {
            val player = Bukkit.getPlayer(playerId)
            val rosePlayer = if (player != null) RosePlayer(player) else RosePlayer(playerId, "Unknown", "default")
            val api = RoseChatAPI.getInstance()
            val currentChannel = rosePlayer.playerData?.currentChannel ?: return

            // If the player is in a channel that is guild-related (based on ID convention or provider)
            // LumaGuilds usually creates channels with 'guild_' prefix or similar if it's integrating.
            // Even if it's just a general cleanup, we check if the channel is one they shouldn't be in.
            
            if (currentChannel.id.startsWith("guild_") || currentChannel.id.contains("guild")) {
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
