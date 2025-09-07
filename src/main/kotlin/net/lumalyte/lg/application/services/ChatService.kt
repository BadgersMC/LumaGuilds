package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.ChatChannel
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import java.util.UUID

/**
 * Service interface for managing guild chat channels and communication.
 */
interface ChatService {
    
    /**
     * Routes a chat message to the appropriate channels based on the sender's context.
     *
     * @param senderId The ID of the player sending the message.
     * @param message The message content.
     * @param targetChannel The intended channel for the message.
     * @return true if the message was routed successfully, false otherwise.
     */
    fun routeMessage(senderId: UUID, message: String, targetChannel: ChatChannel): Boolean
    
    /**
     * Sends a guild announcement to all guild members.
     *
     * @param guildId The ID of the guild.
     * @param announcerId The ID of the player making the announcement.
     * @param message The announcement message.
     * @return true if the announcement was sent successfully, false otherwise.
     */
    fun sendGuildAnnouncement(guildId: UUID, announcerId: UUID, message: String): Boolean
    
    /**
     * Sends a ping to all guild members with sound notification.
     *
     * @param guildId The ID of the guild.
     * @param pingerId The ID of the player sending the ping.
     * @param message Optional message with the ping.
     * @return true if the ping was sent successfully, false otherwise.
     */
    fun sendGuildPing(guildId: UUID, pingerId: UUID, message: String? = null): Boolean
    
    /**
     * Toggles chat visibility for a specific channel for a player.
     *
     * @param playerId The ID of the player.
     * @param channel The channel to toggle.
     * @return The new visibility state for the channel.
     */
    fun toggleChatVisibility(playerId: UUID, channel: ChatChannel): Boolean
    
    /**
     * Gets the chat visibility settings for a player.
     *
     * @param playerId The ID of the player.
     * @return The player's chat visibility settings.
     */
    fun getVisibilitySettings(playerId: UUID): ChatVisibilitySettings
    
    /**
     * Updates chat visibility settings for a player.
     *
     * @param playerId The ID of the player.
     * @param settings The new visibility settings.
     * @return true if the settings were updated successfully, false otherwise.
     */
    fun updateVisibilitySettings(playerId: UUID, settings: ChatVisibilitySettings): Boolean
    
    /**
     * Gets all players who should receive a message in a specific channel.
     *
     * @param senderId The ID of the sender.
     * @param channel The channel the message is being sent to.
     * @return A set of player IDs who should receive the message.
     */
    fun getRecipientsForChannel(senderId: UUID, channel: ChatChannel): Set<UUID>
    
    /**
     * Formats a message with guild/party context and emoji support.
     *
     * @param senderId The ID of the sender.
     * @param message The raw message.
     * @param channel The channel the message is for.
     * @return The formatted message.
     */
    fun formatMessage(senderId: UUID, message: String, channel: ChatChannel): String
    
    /**
     * Checks if a player can use announcements in their guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can send announcements, false otherwise.
     */
    fun canSendAnnouncements(playerId: UUID, guildId: UUID): Boolean
    
    /**
     * Checks if a player can send pings in their guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can send pings, false otherwise.
     */
    fun canSendPings(playerId: UUID, guildId: UUID): Boolean
    
    /**
     * Checks if a player is rate limited for announcements.
     *
     * @param playerId The ID of the player.
     * @return true if the player is rate limited, false otherwise.
     */
    fun isAnnouncementRateLimited(playerId: UUID): Boolean
    
    /**
     * Checks if a player is rate limited for pings.
     *
     * @param playerId The ID of the player.
     * @return true if the player is rate limited, false otherwise.
     */
    fun isPingRateLimited(playerId: UUID): Boolean
    
    /**
     * Processes emoji shortcuts in a message.
     *
     * @param senderId The ID of the sender.
     * @param message The message with potential emoji shortcuts.
     * @return The message with emoji shortcuts processed.
     */
    fun processEmojis(senderId: UUID, message: String): String
    
    /**
     * Gets the online members of a guild for chat purposes.
     *
     * @param guildId The ID of the guild.
     * @return A set of online player IDs in the guild.
     */
    fun getOnlineGuildMembers(guildId: UUID): Set<UUID>
    
    /**
     * Gets all online members of allied guilds.
     *
     * @param guildId The ID of the reference guild.
     * @return A set of online player IDs in allied guilds.
     */
    fun getOnlineAlliedMembers(guildId: UUID): Set<UUID>
    
    /**
     * Gets all online members in the same party as a guild.
     *
     * @param guildId The ID of the reference guild.
     * @return A set of online player IDs in the same party.
     */
    fun getOnlinePartyMembers(guildId: UUID): Set<UUID>
    
    /**
     * Sends a formatted message to a set of recipients.
     *
     * @param recipients The set of player IDs to send the message to.
     * @param message The formatted message to send.
     * @return The number of recipients who received the message.
     */
    fun broadcastMessage(recipients: Set<UUID>, message: String): Int
    
    /**
     * Sends a formatted message with sound notification to recipients.
     *
     * @param recipients The set of player IDs to send the message to.
     * @param message The formatted message to send.
     * @param soundNotification Whether to play a sound notification.
     * @return The number of recipients who received the message.
     */
    fun broadcastMessageWithSound(recipients: Set<UUID>, message: String, soundNotification: Boolean = false): Int
}
