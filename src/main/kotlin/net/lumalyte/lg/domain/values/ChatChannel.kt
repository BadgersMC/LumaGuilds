package net.lumalyte.lg.domain.values

import java.util.UUID

/**
 * Represents different chat channels in the guild system.
 */
enum class ChatChannel {
    /** Guild-only chat channel */
    GUILD,
    
    /** Allied guilds chat channel */
    ALLY,
    
    /** Party chat channel */
    PARTY,
    
    /** Public/global chat channel */
    PUBLIC
}

/**
 * Represents the visibility settings for chat channels for a player.
 *
 * @property playerId The ID of the player these settings belong to.
 * @property guildChatVisible Whether guild chat is visible to the player.
 * @property allyChatVisible Whether ally chat is visible to the player.
 * @property partyChatVisible Whether party chat is visible to the player.
 */
data class ChatVisibilitySettings(
    val playerId: UUID,
    val guildChatVisible: Boolean = true,
    val allyChatVisible: Boolean = true,
    val partyChatVisible: Boolean = true
)

/**
 * Represents rate limiting information for announcements and pings.
 *
 * @property playerId The ID of the player.
 * @property lastAnnounceTime The timestamp of the last announcement.
 * @property lastPingTime The timestamp of the last ping.
 * @property announceCount The number of announcements made in the current window.
 * @property pingCount The number of pings made in the current window.
 */
data class ChatRateLimit(
    val playerId: UUID,
    val lastAnnounceTime: Long = 0,
    val lastPingTime: Long = 0,
    val announceCount: Int = 0,
    val pingCount: Int = 0
)
