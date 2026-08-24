package net.lumalyte.lg.interaction.listeners

import dev.rosewood.rosechat.chat.channel.Channel
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.domain.entities.Guild
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
    private val penaltyService: PenaltyService by inject()
    private val lang: LangService by inject()

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
            player.sendMessage(lang.msg("notification.guild_chat.guild_channel_missing", "channel" to ChatChannelIds.GUILD))
            return false
        }

        val current = adapter.getCurrentChannel(player)

        if (current === guildChannel) {
            // Toggle OFF — restore previous channel, fall back to default.
            leaveAndRestore(player)
            return false
        }

        // Toggle ON — must be in a guild.
        if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
            player.sendMessage(lang.msg("notification.guild_chat.not_in_guild"))
            return false
        }

        // Guild mute blocks joining guild chat (and /gc is blocked separately).
        val guildId = guildService.getPlayerGuilds(player.uniqueId).firstOrNull()?.id
        if (guildId != null && penaltyService.isGuildMuted(guildId)) {
            player.sendMessage(lang.msg("notification.guild_chat.guild_muted"))
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
            player.sendMessage(lang.msg("notification.guild_chat.ally_channel_missing", "channel" to ChatChannelIds.ALLY))
            return false
        }

        val current = adapter.getCurrentChannel(player)

        if (current === allyChannel) {
            leaveAndRestore(player)
            return false
        }

        if (guildService.getPlayerGuilds(player.uniqueId).isEmpty()) {
            player.sendMessage(lang.msg("notification.guild_chat.not_in_guild"))
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
        val modChannel = resolveModChatChannel(player) ?: return null
        val current = adapter.getCurrentChannel(player)

        // Already inside → always allow leaving, even if permission was revoked.
        if (current === modChannel) {
            leaveAndRestore(player)
            return false
        }

        // Entering → check guild membership and MODERATE_CHAT permission.
        return enterModChat(player, modChannel, current)
    }

    private fun leaveAndRestore(player: Player) {
        val prevId = previousChannelId.remove(player.uniqueId)
        val target = prevId?.let { adapter.getChannel(it) } ?: adapter.getDefaultChannel()
        if (target != null) adapter.switchChannel(player, target)
    }

    private fun enterModChat(
        player: Player,
        modChannel: Channel,
        current: Channel?,
    ): Boolean? {
        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("notification.guild_chat.not_in_guild"))
            return null
        }
        if (!hasModeratePermission(player, guilds)) {
            player.sendMessage(lang.msg("notification.guild_chat.mod_permission"))
            return null
        }

        val currentId = current?.id
        if (currentId != null && canCachePrevious(currentId)) {
            previousChannelId[player.uniqueId] = currentId
        }
        adapter.switchChannel(player, modChannel)
        return true
    }

    private fun hasModeratePermission(player: Player, guilds: Set<Guild>): Boolean {
        return guilds.any { guild ->
            memberService.hasPermission(
                player.uniqueId,
                guild.id,
                RankPermission.MODERATE_CHAT,
            )
        }
    }

    private fun resolveModChatChannel(player: Player): Channel? {
        val ch = adapter.getChannel(ChatChannelIds.MODCHAT)
        if (ch == null) {
            player.sendMessage(lang.msg("notification.guild_chat.mod_channel_missing"))
        }
        return ch
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
