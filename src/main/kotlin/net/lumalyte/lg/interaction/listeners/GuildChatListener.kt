package net.lumalyte.lg.interaction.listeners

import dev.rosewood.rosechat.chat.channel.Channel
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ChatChannelIds
import net.lumalyte.lg.infrastructure.services.RealRoseChatAdapter
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
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

    /** Injectable adapter — override in tests to avoid static RoseChat API. */
    @PublishedApi
    internal var adapter: RoseChatAdapter = RealRoseChatAdapter()

    /** Caches the channel a player was in before they switched into guild/ally/mod chat,
     *  so toggle-off restores it. */
    private val previousChannelId: MutableMap<UUID, String> = ConcurrentHashMap()

    /**
     * Toggles guild chat mode for a player by switching their RoseChat channel.
     *
     * @return true if guild chat is now ON, false if now OFF.
     */
    fun toggleGuildChat(player: Player): Boolean {
        val guildChannel = adapter.getChannel(ChatChannelIds.GUILD)
        if (guildChannel == null) {
            player.sendMessage("§c❌ Guild channel '${ChatChannelIds.GUILD}' is not configured in RoseChat. Ask staff to enable it in channels.yml.")
            return false
        }

        val current = adapter.getCurrentChannel(player)

        if (current === guildChannel) {
            // Toggle OFF — restore previous channel, fall back to default.
            val prevId = previousChannelId.remove(player.uniqueId)
            val target = prevId?.let { adapter.getChannel(it) } ?: adapter.getDefaultChannel()
            if (target != null) adapter.switchChannel(player, target)
            return false
        }

        // Toggle ON — must be in a guild.
        if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return false
        }

        val currentId = current?.id
        if (currentId != null && canCachePrevious(currentId)) {
            previousChannelId[player.uniqueId] = currentId
        }
        adapter.switchChannel(player, guildChannel)
        return true
    }

    fun toggleAllyChat(player: Player): Boolean {
        val allyChannel = adapter.getChannel(ChatChannelIds.ALLY)
        if (allyChannel == null) {
            player.sendMessage("§c❌ Ally channel '${ChatChannelIds.ALLY}' is not configured in RoseChat.")
            return false
        }

        val current = adapter.getCurrentChannel(player)

        if (current === allyChannel) {
            val prevId = previousChannelId.remove(player.uniqueId)
            val target = prevId?.let { adapter.getChannel(it) } ?: adapter.getDefaultChannel()
            if (target != null) adapter.switchChannel(player, target)
            return false
        }

        if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return false
        }

        val currentId = current?.id
        if (currentId != null && canCachePrevious(currentId)) {
            previousChannelId[player.uniqueId] = currentId
        }
        adapter.switchChannel(player, allyChannel)
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
        val current = adapter.getCurrentChannel(player)

        // Already inside → always allow leaving, even if permission was revoked.
        if (current === modChannel) {
            val prevId = previousChannelId.remove(player.uniqueId)
            val target = prevId?.let { adapter.getChannel(it) } ?: adapter.getDefaultChannel()
            if (target != null) adapter.switchChannel(player, target)
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

        val currentId = current?.id
        if (currentId != null && canCachePrevious(currentId)) {
            previousChannelId[player.uniqueId] = currentId
        }
        adapter.switchChannel(player, modChannel)
        return true
    }

    /** Resolves the mod chat channel without checking guild permissions. */
    private fun resolveModChatChannelOnly(player: Player): Channel? {
        val ch = adapter.getChannel(ChatChannelIds.MODCHAT)
        return if (ch == null) {
            player.sendMessage("§c❌ Mod chat channel not configured.")
            null
        } else {
            ch
        }
    }

    private fun canCachePrevious(channelId: String): Boolean {
        return channelId != ChatChannelIds.GUILD &&
            channelId != ChatChannelIds.ALLY &&
            channelId != ChatChannelIds.MODCHAT
    }

    fun isInGuildChatMode(playerId: UUID): Boolean {
        val player = Bukkit.getPlayer(playerId) ?: return false
        return adapter.getCurrentChannel(player)?.id == ChatChannelIds.GUILD
    }

    /** Called when a player quits or is removed from a guild. */
    fun removePlayer(playerId: UUID) {
        previousChannelId.remove(playerId)
    }
}
