package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.RelationService
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
import org.slf4j.LoggerFactory
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
    private val memberService: MemberService by inject()
    private val partyService: PartyService by inject()
    private val relationService: RelationService by inject()

    private val logger = LoggerFactory.getLogger(RoseChatCleanupListener::class.java)

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
        // Batch: resolve RoseChat API once, then process all members
        val api = RoseChatAPI.getInstance() ?: return
        event.memberIds.forEach { memberId ->
            val player = Bukkit.getPlayer(memberId) ?: return@forEach
            validateAndCleanup(player, api)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRelationChange(event: GuildRelationChangeEvent) {
        // If an alliance is broken, refresh all members of both guilds
        if (event.newRelationType == RelationType.NEUTRAL || event.newRelationType == RelationType.ENEMY) {
            val api = RoseChatAPI.getInstance() ?: return
            // Resolve guild UUIDs to Guild objects, then collect member IDs
            val guild1 = guildService.getGuild(event.guild1)
            val guild2 = guildService.getGuild(event.guild2)
            val memberIds = mutableSetOf<UUID>()
            guild1?.let { memberIds.addAll(memberService.getGuildMembers(it.id).map { m -> m.playerId }) }
            guild2?.let { memberIds.addAll(memberService.getGuildMembers(it.id).map { m -> m.playerId }) }
            memberIds.forEach { memberId ->
                val player = Bukkit.getPlayer(memberId) ?: return@forEach
                validateAndCleanup(player, api)
            }
        }
    }

    /**
     * Checks if the player's current RoseChat channel is still valid based on their
     * LumaGuilds status (guild membership and party participation).
     */
    private fun validateAndCleanup(player: Player, api: RoseChatAPI? = RoseChatAPI.getInstance()) {
        if (api == null || !player.isOnline) return

        try {
            val rosePlayer = RosePlayer(player)
            val currentChannel = rosePlayer.playerData?.currentChannel ?: return
            val channelId = currentChannel.id

            val shouldSwitch = when {
                // Fixed 'guild' channel: valid only while the player is in a guild.
                channelId == guildChannelId ->
                    guildService.getPlayerGuilds(player.uniqueId).isEmpty()

                // Fixed 'guild-ally' channel: valid only while one of the player's
                // guilds still has at least one active alliance.
                channelId == allyChannelId ->
                    guildService.getPlayerGuilds(player.uniqueId).none { guild ->
                        relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
                            .any { it.isActive() }
                    }

                // Dynamic party channels are keyed by the party UUID. Channels whose
                // ID is not a UUID belong to other plugins and must not be touched.
                else -> {
                    val channelUuid = runCatching { UUID.fromString(channelId) }.getOrNull()
                    if (channelUuid == null) {
                        false
                    } else {
                        val activePartyIds = guildService.getPlayerGuilds(player.uniqueId)
                            .flatMap { partyService.getActivePartiesForGuild(it.id) }
                            .map { it.id }
                            .toSet()
                        channelUuid !in activePartyIds
                    }
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
            logger.debug("Error during RoseChat cleanup for player ${player.uniqueId}", e)
        }
    }
}
