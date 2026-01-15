package net.lumalyte.lg.infrastructure.hytale.services

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.Universe
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.domain.values.ChatChannel
import net.lumalyte.lg.domain.values.ChatRateLimit
import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Hytale implementation of ChatService using Hytale's Message API.
 *
 * This service manages guild chat channels, announcements, and message routing using
 * Hytale's native Message system which supports MiniMessage formatting.
 *
 * Features:
 * - Multi-channel chat (Guild, Ally, Party, Public)
 * - Guild announcements with formatting
 * - Ping notifications
 * - Rate limiting for announcements and pings
 * - Per-player chat visibility settings
 */
class HytaleChatService(
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val playerService: PlayerService
) : ChatService {

    private val log = LoggerFactory.getLogger(HytaleChatService::class.java)

    // In-memory storage for chat settings and rate limits
    // TODO: Move to persistent storage (database) in the future
    private val visibilitySettings = ConcurrentHashMap<UUID, ChatVisibilitySettings>()
    private val rateLimits = ConcurrentHashMap<UUID, ChatRateLimit>()

    // Rate limit configuration (in milliseconds)
    private val ANNOUNCE_COOLDOWN_MS = 300000L // 5 minutes
    private val PING_COOLDOWN_MS = 60000L // 1 minute
    private val ANNOUNCE_MAX_PER_HOUR = 3
    private val PING_MAX_PER_HOUR = 10

    override fun routeMessage(senderId: UUID, message: String, targetChannel: ChatChannel): Boolean {
        val senderName = playerService.getPlayerName(senderId) ?: "Unknown"

        // Get recipients for the channel
        val recipients = getRecipientsForChannel(senderId, targetChannel)

        if (recipients.isEmpty()) {
            log.debug("No recipients found for message from $senderName in channel $targetChannel")
            return false
        }

        // Format the message with channel context
        val formattedMessage = formatMessage(senderId, message, targetChannel)

        // Broadcast to recipients
        broadcastMessage(recipients, formattedMessage)

        return true
    }

    override fun sendGuildAnnouncement(guildId: UUID, announcerId: UUID, message: String): Boolean {
        // Check permissions
        if (!canSendAnnouncements(announcerId, guildId)) {
            log.debug("Player $announcerId cannot send announcements in guild $guildId")
            return false
        }

        // Check rate limit
        if (isAnnouncementRateLimited(announcerId)) {
            log.debug("Player $announcerId is rate limited for announcements")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false
        val announcerName = playerService.getPlayerName(announcerId) ?: "Unknown"

        // Get all online guild members
        val recipients = getOnlineGuildMembers(guildId)

        if (recipients.isEmpty()) {
            log.debug("No online members to receive announcement in guild ${guild.name}")
            return false
        }

        // Format announcement with guild styling
        val announcementMessage = Message.raw("[")
            .color("#FFD700") // Gold color for announcement prefix
            .bold(true)
            .insert("ANNOUNCEMENT")
            .insert("] ")
            .color("#FFFFFF")
            .bold(false)
            .insert("<")
            .insert(guild.name)
            .insert("> ")
            .color("#00FFFF") // Cyan for announcer name
            .insert(announcerName)
            .insert(": ")
            .color("#FFFFFF")
            .insert(message)

        // Send to all recipients
        for (recipientId in recipients) {
            val playerRef = Universe.get().getPlayer(recipientId)
            playerRef?.sendMessage(announcementMessage)
        }

        // Update rate limit
        updateAnnouncementRateLimit(announcerId)

        return true
    }

    override fun sendGuildPing(guildId: UUID, pingerId: UUID, message: String?): Boolean {
        // Check permissions
        if (!canSendPings(pingerId, guildId)) {
            log.debug("Player $pingerId cannot send pings in guild $guildId")
            return false
        }

        // Check rate limit
        if (isPingRateLimited(pingerId)) {
            log.debug("Player $pingerId is rate limited for pings")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false
        val pingerName = playerService.getPlayerName(pingerId) ?: "Unknown"

        // Get all online guild members
        val recipients = getOnlineGuildMembers(guildId)

        if (recipients.isEmpty()) {
            log.debug("No online members to ping in guild ${guild.name}")
            return false
        }

        // Format ping message
        val pingMessage = Message.raw("[")
            .color("#FF5555") // Red color for ping
            .bold(true)
            .insert("PING")
            .insert("] ")
            .color("#FFFFFF")
            .bold(false)
            .insert("<")
            .insert(guild.name)
            .insert("> ")
            .color("#00FFFF")
            .insert(pingerName)

        if (message != null) {
            pingMessage
                .insert(": ")
                .color("#FFFFFF")
                .insert(message)
        }

        // Send to all recipients
        val pingText = pingMessage.getRawText() ?: "[PING] ${guild.name} - $pingerName${if (message != null) ": $message" else ""}"
        broadcastMessageWithSound(recipients, pingText, soundNotification = true)

        // Update rate limit
        updatePingRateLimit(pingerId)

        return true
    }

    override fun toggleChatVisibility(playerId: UUID, channel: ChatChannel): Boolean {
        val settings = getVisibilitySettings(playerId)

        val newSettings = when (channel) {
            ChatChannel.GUILD -> settings.copy(guildChatVisible = !settings.guildChatVisible)
            ChatChannel.ALLY -> settings.copy(allyChatVisible = !settings.allyChatVisible)
            ChatChannel.PARTY -> settings.copy(partyChatVisible = !settings.partyChatVisible)
            ChatChannel.PUBLIC -> settings // Public chat cannot be toggled
        }

        return updateVisibilitySettings(playerId, newSettings)
    }

    override fun getVisibilitySettings(playerId: UUID): ChatVisibilitySettings {
        return visibilitySettings.getOrPut(playerId) {
            ChatVisibilitySettings(playerId)
        }
    }

    override fun updateVisibilitySettings(playerId: UUID, settings: ChatVisibilitySettings): Boolean {
        visibilitySettings[playerId] = settings
        return true
    }

    override fun getRecipientsForChannel(senderId: UUID, channel: ChatChannel): Set<UUID> {
        val senderGuilds = memberRepository.getGuildsByPlayer(senderId)

        return when (channel) {
            ChatChannel.GUILD -> {
                // Get all online members of sender's guilds who have guild chat visible
                senderGuilds.flatMap { guildId ->
                    getOnlineGuildMembers(guildId).filter { recipientId ->
                        getVisibilitySettings(recipientId).guildChatVisible
                    }
                }.toSet()
            }
            ChatChannel.ALLY -> {
                // TODO: Implement alliance system
                // For now, return empty set as alliances aren't implemented yet
                emptySet()
            }
            ChatChannel.PARTY -> {
                // TODO: Implement party system
                // For now, return empty set as parties aren't implemented yet
                emptySet()
            }
            ChatChannel.PUBLIC -> {
                // Return all online players
                playerService.getOnlinePlayers().toSet()
            }
        }
    }

    override fun formatMessage(senderId: UUID, message: String, channel: ChatChannel): String {
        val senderName = playerService.getPlayerName(senderId) ?: "Unknown"
        val senderGuilds = memberRepository.getGuildsByPlayer(senderId)

        // Get guild tag if player is in a guild
        val guildTag = if (senderGuilds.isNotEmpty()) {
            val guild = guildRepository.getById(senderGuilds.first())
            guild?.tag ?: guild?.name?.let { "[$it]" }
        } else {
            null
        }

        // Process emojis in message
        val processedMessage = processEmojis(senderId, message)

        // Format based on channel
        val channelPrefix = when (channel) {
            ChatChannel.GUILD -> "[G]"
            ChatChannel.ALLY -> "[A]"
            ChatChannel.PARTY -> "[P]"
            ChatChannel.PUBLIC -> ""
        }

        // Build formatted message
        val formattedBuilder = StringBuilder()
        if (channelPrefix.isNotEmpty()) {
            formattedBuilder.append("<#55FF55>$channelPrefix</#55FF55> ")
        }
        if (guildTag != null) {
            formattedBuilder.append("$guildTag ")
        }
        formattedBuilder.append("<#00FFFF>$senderName</#00FFFF>: ")
        formattedBuilder.append("<white>$processedMessage</white>")

        return formattedBuilder.toString()
    }

    override fun canSendAnnouncements(playerId: UUID, guildId: UUID): Boolean {
        // Check if player is a member of the guild
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false

        // TODO: Check rank permissions when rank system is implemented
        // For now, all guild members can send announcements
        return true
    }

    override fun canSendPings(playerId: UUID, guildId: UUID): Boolean {
        // Check if player is a member of the guild
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false

        // TODO: Check rank permissions when rank system is implemented
        // For now, all guild members can send pings
        return true
    }

    override fun isAnnouncementRateLimited(playerId: UUID): Boolean {
        val rateLimit = rateLimits.getOrPut(playerId) {
            ChatRateLimit(playerId)
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAnnounce = currentTime - rateLimit.lastAnnounceTime

        // Check if cooldown has passed
        if (timeSinceLastAnnounce < ANNOUNCE_COOLDOWN_MS) {
            return true
        }

        // Check hourly limit
        val oneHourAgo = currentTime - 3600000L
        if (rateLimit.lastAnnounceTime > oneHourAgo && rateLimit.announceCount >= ANNOUNCE_MAX_PER_HOUR) {
            return true
        }

        return false
    }

    override fun isPingRateLimited(playerId: UUID): Boolean {
        val rateLimit = rateLimits.getOrPut(playerId) {
            ChatRateLimit(playerId)
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastPing = currentTime - rateLimit.lastPingTime

        // Check if cooldown has passed
        if (timeSinceLastPing < PING_COOLDOWN_MS) {
            return true
        }

        // Check hourly limit
        val oneHourAgo = currentTime - 3600000L
        if (rateLimit.lastPingTime > oneHourAgo && rateLimit.pingCount >= PING_MAX_PER_HOUR) {
            return true
        }

        return false
    }

    override fun processEmojis(senderId: UUID, message: String): String {
        // TODO: Implement emoji processing with Hytale's asset system
        // For now, return message as-is
        // Future: Replace :emoji: shortcuts with actual emoji characters or asset references
        return message
    }

    override fun getOnlineGuildMembers(guildId: UUID): Set<UUID> {
        val members = memberRepository.getByGuild(guildId)
        return members.mapNotNull { member ->
            if (playerService.isPlayerOnline(member.playerId)) {
                member.playerId
            } else {
                null
            }
        }.toSet()
    }

    override fun getOnlineAlliedMembers(guildId: UUID): Set<UUID> {
        // TODO: Implement when alliance system is added
        return emptySet()
    }

    override fun getOnlinePartyMembers(guildId: UUID): Set<UUID> {
        // TODO: Implement when party system is added
        return emptySet()
    }

    override fun broadcastMessage(recipients: Set<UUID>, message: String): Int {
        var sentCount = 0

        // Parse message with MiniMessage formatting
        val hytaleMessage = Message.parse(message)

        for (recipientId in recipients) {
            val playerRef = Universe.get().getPlayer(recipientId)
            if (playerRef != null) {
                playerRef.sendMessage(hytaleMessage)
                sentCount++
            }
        }

        return sentCount
    }

    override fun broadcastMessageWithSound(recipients: Set<UUID>, message: String, soundNotification: Boolean): Int {
        val sentCount = broadcastMessage(recipients, message)

        // Play sound notification if requested
        if (soundNotification) {
            for (recipientId in recipients) {
                // TODO: Use Hytale sound API when available
                // For now, just send the message (sound implementation pending)
                log.debug("Sound notification for player $recipientId (not yet implemented)")
            }
        }

        return sentCount
    }

    // Private helper methods

    private fun updateAnnouncementRateLimit(playerId: UUID) {
        val currentTime = System.currentTimeMillis()
        val rateLimit = rateLimits.getOrPut(playerId) {
            ChatRateLimit(playerId)
        }

        val oneHourAgo = currentTime - 3600000L
        val newCount = if (rateLimit.lastAnnounceTime > oneHourAgo) {
            rateLimit.announceCount + 1
        } else {
            1
        }

        rateLimits[playerId] = rateLimit.copy(
            lastAnnounceTime = currentTime,
            announceCount = newCount
        )
    }

    private fun updatePingRateLimit(playerId: UUID) {
        val currentTime = System.currentTimeMillis()
        val rateLimit = rateLimits.getOrPut(playerId) {
            ChatRateLimit(playerId)
        }

        val oneHourAgo = currentTime - 3600000L
        val newCount = if (rateLimit.lastPingTime > oneHourAgo) {
            rateLimit.pingCount + 1
        } else {
            1
        }

        rateLimits[playerId] = rateLimit.copy(
            lastPingTime = currentTime,
            pingCount = newCount
        )
    }
}
