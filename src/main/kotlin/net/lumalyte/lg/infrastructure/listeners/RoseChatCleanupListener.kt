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
internal class RoseChatCleanupListener(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val partyService: PartyService,
    private val relationService: RelationService
) : Listener {

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
     * Decides whether [playerId] should be removed from the RoseChat channel [channelId]
     * based purely on their current LumaGuilds status.
     *
     * - The fixed `guild` channel is valid only while the player is in a guild.
     * - The fixed `guild-ally` channel is valid only while one of the player's guilds
     *   still has at least one active alliance.
     * - Dynamic party channels are keyed by the party UUID; the player may stay only
     *   if that UUID is an active party of one of their guilds. Channels whose id is
     *   not a UUID belong to other plugins and are never touched.
     */
    fun shouldLeaveChannel(playerId: UUID, channelId: String): Boolean = when (channelId) {
        guildChannelId ->
            guildService.getPlayerGuilds(playerId).isEmpty()

        allyChannelId ->
            guildService.getPlayerGuilds(playerId).none { guild ->
                relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
                    .any { it.isActive() }
            }

        else -> {
            val channelUuid = runCatching { UUID.fromString(channelId) }.getOrNull()
            if (channelUuid == null) {
                false
            } else {
                val activePartyIds = guildService.getPlayerGuilds(playerId)
                    .flatMap { partyService.getActivePartiesForGuild(it.id) }
                    .map { it.id }
                    .toSet()
                channelUuid !in activePartyIds
            }
        }
    }

    private fun trySwitchToDefaultChannel(player: Player, api: RoseChatAPI) {
        val rosePlayer = RosePlayer(player)
        val currentChannel = rosePlayer.playerData?.currentChannel ?: return
        val channelId = currentChannel.id

        if (shouldLeaveChannel(player.uniqueId, channelId)) {
            val defaultChannel = api.channelManager.defaultChannel
            if (defaultChannel != null && channelId != defaultChannel.id) {
                rosePlayer.switchChannel(defaultChannel)
                player.sendMessage(
                    "§7[§6!§7] §eYou have been moved to Global chat because you are no longer in " +
                        "that channel's guild/party.",
                )
            }
        }
    }

    private fun validateAndCleanup(player: Player, api: RoseChatAPI? = RoseChatAPI.getInstance()) {
        if (api == null || !player.isOnline) return

        try {
            trySwitchToDefaultChannel(player, api)
        } catch (e: NoClassDefFoundError) {
            logger.debug("RoseChat not available for cleanup", e)
        } catch (e: IllegalStateException) {
            logger.debug("RoseChat state error during cleanup for player ${player.uniqueId}", e)
        }
    }
}
