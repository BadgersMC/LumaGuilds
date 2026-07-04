package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.api.RoseChatAPI
import dev.rosewood.rosechat.message.RosePlayer
import org.bukkit.entity.Player

/**
 * One-shot helper that sends a single message to a RoseChat channel without
 * changing the player's current channel. RoseChat remains the single authority
 * for routing, formatting, filters, mutes, spy, events, and Discord integration.
 *
 * RoseChat is a REQUIRED dependency — LumaGuilds directly imports its classes,
 * so it's declared in plugin.yml as `depend: [RoseChat]`.
 */
object RoseChatQuickChat {

    /** Outcome of a quick-chat attempt. */
    sealed interface Result {
        /** Message was sent successfully via RoseChat. */
        data object Sent : Result

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
    fun send(player: Player, channelId: String, message: String): Result {
        if (message.isBlank()) return Result.EmptyMessage

        val channel = RoseChatAPI.getInstance().channelManager.getChannel(channelId)
            ?: return Result.ChannelMissing

        RosePlayer(player).quickChat(channel, message)
        return Result.Sent
    }
}
