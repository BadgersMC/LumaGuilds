package net.lumalyte.lg.interaction.listeners

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.message.RosePlayer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ChatChannelIds
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * /g chat toggles the player's RoseChat current channel between the configured guild
 * channel and their previous channel. RoseChat owns chat routing via its
 * LumaGuildsChannel hook — this class no longer intercepts AsyncChatEvent.
 *
 * The Listener interface is kept for compatibility with existing DI and command wiring
 * but no @EventHandler methods are declared and the class is no longer registered as
 * a Bukkit listener.
 */
class GuildChatListener : Listener, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    private val logger = LoggerFactory.getLogger(GuildChatListener::class.java)

    /** ID of the RoseChat channel that targets the sender's guild. Must match channels.yml. */
    private val guildChannelId = ChatChannelIds.GUILD

    /** ID of the RoseChat channel that targets allied guilds. Must match channels.yml. */
    private val allyChannelId = ChatChannelIds.ALLY

    private val modChatChannelId = ChatChannelIds.MODCHAT

    /** Caches the channel a player was in before they switched into guild/ally chat, so toggle-off restores it. */
    private val previousChannelId: MutableMap<UUID, String> = ConcurrentHashMap()

    /**
     * Toggles guild chat mode for a player by switching their RoseChat channel.
     *
     * @return true if guild chat is now ON, false if now OFF.
     */
    fun toggleGuildChat(player: Player): Boolean {
        val api = RoseChatAPI.getInstance()
        if (api == null) {
            player.sendMessage("§c❌ RoseChat is not loaded — guild chat unavailable.")
            return false
        }

        val rose = RosePlayer(player)
        val current: Channel? = rose.playerData?.currentChannel
        val guildChannel: Channel? = api.channelManager.getChannel(guildChannelId)

        if (guildChannel == null) {
            player.sendMessage("§c❌ Guild channel '$guildChannelId' is not configured in RoseChat. Ask staff to enable it in channels.yml.")
            return false
        }

        if (current === guildChannel) {
            // Toggle OFF — restore previous channel, fall back to default.
            val prevId = previousChannelId.remove(player.uniqueId)
            val target = prevId?.let { api.channelManager.getChannel(it) } ?: api.defaultChannel
            if (target != null) rose.switchChannel(target)
            return false
        }

        // Toggle ON — must be in a guild.
        if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return false
        }

        if (current != null && canCachePrevious(current.id)) {
            previousChannelId[player.uniqueId] = current.id
        }
        rose.switchChannel(guildChannel)
        return true
    }

    fun toggleAllyChat(player: Player): Boolean {
        val api = RoseChatAPI.getInstance()
        if (api == null) {
            player.sendMessage("§c❌ RoseChat is not loaded — ally chat unavailable.")
            return false
        }

        val rose = RosePlayer(player)
        val current: Channel? = rose.playerData?.currentChannel
        val allyChannel: Channel? = api.channelManager.getChannel(allyChannelId)

        if (allyChannel == null) {
            player.sendMessage("§c❌ Ally channel '$allyChannelId' is not configured in RoseChat.")
            return false
        }

        if (current === allyChannel) {
            val prevId = previousChannelId.remove(player.uniqueId)
            val target = prevId?.let { api.channelManager.getChannel(it) } ?: api.defaultChannel
            if (target != null) rose.switchChannel(target)
            return false
        }

        if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return false
        }

        if (current != null && canCachePrevious(current.id)) {
            previousChannelId[player.uniqueId] = current.id
        }
        rose.switchChannel(allyChannel)
        return true
    }

    /**
     * Toggles mod chat mode. Resolves the channel first, then:
     * - If already in mod chat → always allow leaving, even without permission.
     * - If entering → enforce guild membership and MODERATE_CHAT.
     *
     * @return true if mod chat is now ON, false if toggled OFF,
     *         null if unavailable (error message already sent).
     */
    fun toggleModChat(player: Player): Boolean? {
        val modChannel = resolveModChatChannelOnly(player) ?: return null
        val rose = RosePlayer(player)
        val isCurrentlyInModChat = rose.playerData?.currentChannel === modChannel

        // Already inside → always allow leaving, even if permission was revoked.
        if (isCurrentlyInModChat) {
            val prevId = previousChannelId.remove(player.uniqueId)
            val api = RoseChatAPI.getInstance() ?: return null
            val target = prevId?.let { api.channelManager.getChannel(it) } ?: api.defaultChannel
            if (target != null) rose.switchChannel(target)
            return false
        }

        // Entering → check guild membership and MODERATE_CHAT permission.
        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return null
        }
        if (guilds.none { guild ->
                memberService.hasPermission(
                    player.uniqueId, guild.id, RankPermission.MODERATE_CHAT,
                )
            }
        ) {
            player.sendMessage("§c❌ Only guild moderators can use mod chat!")
            return null
        }

        if (rose.playerData?.currentChannel != null &&
            canCachePrevious(rose.playerData!!.currentChannel!!.id)
        ) {
            previousChannelId[player.uniqueId] = rose.playerData!!.currentChannel!!.id
        }
        rose.switchChannel(modChannel)
        return true
    }

    /** Resolves the mod chat channel without checking guild permissions. */
    private fun resolveModChatChannelOnly(player: Player): Channel? {
        val api = RoseChatAPI.getInstance()
        val ch = api?.channelManager?.getChannel(modChatChannelId)
        return if (ch == null) {
            player.sendMessage("§c❌ Mod chat channel not configured.")
            null
        } else {
            ch
        }
    }

    private fun canCachePrevious(channelId: String): Boolean {
        return channelId != guildChannelId &&
            channelId != allyChannelId &&
            channelId != modChatChannelId
    }

    fun isInGuildChatMode(playerId: UUID): Boolean {
        val player = Bukkit.getPlayer(playerId) ?: return false
        return RosePlayer(player).playerData?.currentChannel?.id == guildChannelId
    }

    /** Called when a player quits or is removed from a guild. */
    fun removePlayer(playerId: UUID) {
        previousChannelId.remove(playerId)
    }
}
