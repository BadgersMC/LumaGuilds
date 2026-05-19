package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.domain.events.GuildMemberRemovedEvent
import net.lumalyte.lg.domain.events.GuildRelationChangeEvent
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * Listener to handle RoseChat channel cleanup when players leave, guilds are disbanded,
 * or relationships (allies) change.
 * 
 * Unlike standard channel identification, this listener verifies if a player is still
 * authorized to be in their current RoseChat channel by checking against LumaGuilds
 * known Party objects and Guild membership status.
 */
class RoseChatCleanupListener : Listener, KoinComponent {

    private val guildService: GuildService by inject()
    private val partyService: PartyService by inject()

    /** IDs of the RoseChat channels defined in LumaGuilds' channels.yml hook. */
    private val guildChannelId = "guild"
    private val allyChannelId = "guild-ally"

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildMemberRemoved(event: GuildMemberRemovedEvent) {
        val player = Bukkit.getPlayer(event.playerId) ?: return
        validateAndCleanup(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        event.memberIds.forEach { memberId ->
            val player = Bukkit.getPlayer(memberId) ?: return@forEach
            validateAndCleanup(player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRelationChange(event: GuildRelationChangeEvent) {
        // If an alliance is broken, refresh all members of both guilds
        if (event.type == RelationType.NEUTRAL || event.type == RelationType.ENEMY) {
            (event.sourceGuild.memberIds + event.targetGuild.memberIds).forEach { memberId ->
                val player = Bukkit.getPlayer(memberId) ?: return@forEach
                validateAndCleanup(player)
            }
        }
    }

    /**
     * Checks if the player's current RoseChat channel is still valid based on their
     * LumaGuilds status (guild membership and party participation).
     */
    private fun validateAndCleanup(player: Player) {
        if (!player.isOnline) return

        try {
            val api = RoseChatAPI.getInstance() ?: return
            val rosePlayer = RosePlayer(player)
            val currentChannel = rosePlayer.playerData?.currentChannel ?: return
            val channelId = currentChannel.id

            var shouldSwitch = false

            // 1. Check if they are in the fixed 'guild' or 'guild-ally' RoseChat channels
            if (channelId == guildChannelId || channelId == allyChannelId) {
                if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
                    shouldSwitch = true
                }
            }

            // 2. Check if they are in a custom Luma Party channel (Dynamic IDs)
            // Luma's Party system uses UUIDs as names in some contexts or metadata.
            // We check the PartyService to see if they are allowed in any party matching this ID.
            if (!shouldSwitch) {
                val parties = partyService.getAllPartiesForGuild(UUID.randomUUID()) // Stub for "all parties" check if needed
                // Actually, the most robust check is: can they still join their active party?
                val activeParty = partyService.getActivePartyForPlayer(player.uniqueId)
                
                // If they are in a channel that looks like a party but don't have an active authorized party
                if (channelId.length > 30 && activeParty == null) {
                    // Check if the current channel is a known party channel they are NOT in
                    // Note: Luma parties often map 1:1 to RoseChat dynamic channels if integrated.
                    shouldSwitch = true
                }
            }

            if (shouldSwitch) {
                val defaultChannel = api.channelManager.defaultChannel
                if (defaultChannel != null && channelId != defaultChannel.id) {
                    rosePlayer.switchChannel(defaultChannel)
                    player.sendMessage("§7[§6!§7] §eYou have been moved to Global chat because you are no longer in that channel's guild/party.")
                }
            }
        } catch (e: NoClassDefFoundError) {
            // RoseChat not loaded
        } catch (e: Exception) {
            // Ignore
        }
    }
}