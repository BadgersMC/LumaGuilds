package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.events.GuildRelationChangeEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Ensures players are not stuck in the ally chat channel after alliances end.
 *
 * When any relation becomes NEUTRAL, all online members of the affected guilds who
 * are currently in the ally chat channel are checked. If their guild no longer has
 * ANY active alliances, they are switched back to the default channel.
 *
 * When a player joins, the same validation is applied so offline players who missed
 * the live event are cleaned up at login.
 */
class GuildRelationChatListener : Listener, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()

    private val logger = LoggerFactory.getLogger(GuildRelationChatListener::class.java)
    private val allyChannelId = "guild-ally"

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildRelationChange(event: GuildRelationChangeEvent) {
        if (event.newRelationType != RelationType.NEUTRAL) return

        val api = RoseChatAPI.getInstance() ?: return
        val allyChannel = api.channelManager.getChannel(allyChannelId) ?: return

        val guildA = event.guild1
        val guildB = event.guild2

        logger.info("Relation ended between $guildA and $guildB — validating ally chat membership")

        val affectedIds = mutableSetOf<UUID>()
        affectedIds.addAll(memberService.getGuildMembers(guildA).map { it.playerId })
        affectedIds.addAll(memberService.getGuildMembers(guildB).map { it.playerId })

        for (playerId in affectedIds) {
            val player = Bukkit.getPlayer(playerId) ?: continue
            maybeExitAllyChat(api, allyChannel, player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val api = RoseChatAPI.getInstance() ?: return
        val allyChannel = api.channelManager.getChannel(allyChannelId) ?: return
        maybeExitAllyChat(api, allyChannel, event.player)
    }

    /**
     * Checks whether [player] should remain in the ally chat channel.
     * If they have no active alliances, switches them to the default channel.
     */
    private fun maybeExitAllyChat(api: RoseChatAPI, allyChannel: Channel, player: Player) {
        val rose = RosePlayer(player)
        val current = rose.playerData?.currentChannel
        if (current !== allyChannel) return

        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            switchToDefault(api, rose, player)
            return
        }

        val guild = guilds.first()
        val hasAllies = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .any { it.isActive() }

        if (!hasAllies) {
            switchToDefault(api, rose, player)
        }
    }

    private fun switchToDefault(api: RoseChatAPI, rose: RosePlayer, player: Player) {
        val default = api.defaultChannel
        if (default != null) {
            rose.switchChannel(default)
            player.sendMessage("§7Your alliance has ended — you've been returned to the default chat channel.")
        }
    }
}
