package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.message.RosePlayer
import org.bukkit.entity.Player

/**
 * Decouples chat commands and the toggle listener from RoseChat's static API so
 * they can be unit-tested with mock channels and a no-op quickChat/switchChannel.
 */
@PublishedApi
internal interface RoseChatAdapter {
    /** Resolves a RoseChat channel by its configured ID, or `null`. */
    fun getChannel(channelId: String): Channel?

    /** Invokes [RosePlayer.quickChat] for the given player and channel. */
    fun quickChat(player: Player, channel: Channel, message: String)

    /** Returns the player's current RoseChat channel, or `null`. */
    fun getCurrentChannel(player: Player): Channel?

    /** Switches the player to [channel] persistently — use for toggles, not one-shots. */
    fun switchChannel(player: Player, channel: Channel)

    /** Returns RoseChat's default channel, or `null`. */
    fun getDefaultChannel(): Channel?
}

/** Production adapter wired to the real RoseChat API. */
internal class RealRoseChatAdapter : RoseChatAdapter {
    override fun getChannel(channelId: String): Channel? {
        return RoseChatAPI.getInstance().channelManager.getChannel(channelId)
    }

    override fun quickChat(player: Player, channel: Channel, message: String) {
        RosePlayer(player).quickChat(channel, message)
    }

    override fun getCurrentChannel(player: Player): Channel? {
        return RosePlayer(player).playerData?.currentChannel
    }

    override fun switchChannel(player: Player, channel: Channel) {
        RosePlayer(player).switchChannel(channel)
    }

    override fun getDefaultChannel(): Channel? {
        return RoseChatAPI.getInstance().defaultChannel
    }
}

/**
 * One-shot helper that sends a single message to a RoseChat channel without
 * changing the player's current channel. RoseChat remains the single authority
 * for routing, formatting, filters, mutes, spy, events, and Discord integration.
 *
 * RoseChat is a REQUIRED dependency — LumaGuilds directly imports its classes,
 * so it's declared in plugin.yml as `depend: [RoseChat]`.
 */
object RoseChatQuickChat {
    /** Injectable adapter — override in tests to avoid static RoseChat API. */
    @PublishedApi
    internal var adapter: RoseChatAdapter = RealRoseChatAdapter()

    /** Outcome of a quick-chat attempt. */
    internal sealed interface Result {
        /** Message was dispatched to RoseChat for delivery. Does NOT guarantee
         *  final delivery — RoseChat may cancel through mutes, filters, or events. */
        data object Dispatched : Result

        /** The requested channel ID is not configured in RoseChat. */
        data object ChannelMissing : Result

        /** The message is blank — nothing to send. */
        data object EmptyMessage : Result
    }

    /**
     * Sends [message] to the RoseChat channel identified by [channelId].
     *
     * Does **not** change the player's current channel — uses RoseChat's
     * quickChat API which temporarily sets the channel for the single message
     * then resets it.
     *
     * @param player    the sending player.
     * @param channelId RoseChat channel ID (e.g., "guild", "guild-ally", "guild-modchat").
     * @param message   the message text.
     * @return a [Result] summarising the outcome.
     */
    internal fun send(player: Player, channelId: String, message: String): Result {
        if (message.isBlank()) return Result.EmptyMessage
        val channel = adapter.getChannel(channelId) ?: return Result.ChannelMissing
        adapter.quickChat(player, channel, message)
        return Result.Dispatched
    }
}
