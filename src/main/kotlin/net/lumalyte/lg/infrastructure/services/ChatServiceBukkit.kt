package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.*
import net.lumalyte.lg.utils.GuildDisplayUtils
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.slf4j.LoggerFactory
import java.util.UUID

class ChatServiceBukkit(
    private val chatSettingsRepository: ChatSettingsRepository,
    private val memberService: MemberService,
    private val guildService: GuildService,
    private val relationService: RelationService,
    private val partyService: PartyService,
    private val nexoEmojiService: NexoEmojiService,
    private val configService: ConfigService,
    private val rankService: RankService,
    private val preferenceRepository: PlayerPartyPreferenceRepository,
    private val partyRepository: PartyRepository
) : ChatService {
    
    private val logger = LoggerFactory.getLogger(ChatServiceBukkit::class.java)
    
    // Rate limiting configuration (in milliseconds)
    private val announceRateLimit = 300000L // 5 minutes
    private val pingRateLimit = 60000L // 1 minute
    private val maxAnnouncementsPerHour = 3
    private val maxPingsPerHour = 10
    
    override fun routeMessage(senderId: UUID, message: String, targetChannel: ChatChannel): Boolean {
        try {
            val recipients = getRecipientsForChannel(senderId, targetChannel)
            if (recipients.isEmpty()) {
                logger.debug("No recipients found for message from player $senderId in channel $targetChannel")
                return false
            }

            val formattedMessage = formatMessage(senderId, message, targetChannel)

            // Handle party chat priority to avoid conflicts with other chat plugins
            if (targetChannel == ChatChannel.PARTY) {
                val config = configService.loadConfig().party
                val priority = config.partyChatPriority

                // Send with configured priority - higher numbers = higher priority
                val deliveredCount = broadcastMessageWithPriority(recipients, formattedMessage, priority)
                logger.debug("Party message from player $senderId delivered to $deliveredCount recipients with priority $priority")
                return deliveredCount > 0
            }

            val deliveredCount = broadcastMessage(recipients, formattedMessage)
            logger.debug("Message from player $senderId delivered to $deliveredCount recipients in channel $targetChannel")
            return deliveredCount > 0
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error routing message", e)
            return false
        }
    }
    
    override fun sendGuildAnnouncement(guildId: UUID, announcerId: UUID, message: String): Boolean {
        try {
            if (!canSendAnnouncements(announcerId, guildId)) {
                logger.warn("Player $announcerId cannot send announcements for guild $guildId")
                return false
            }
            
            if (isAnnouncementRateLimited(announcerId)) {
                logger.warn("Player $announcerId is rate limited for announcements")
                return false
            }
            
            val guild = guildService.getGuild(guildId)
            if (guild == null) {
                logger.warn("Guild $guildId not found")
                return false
            }
            
            val announcerName = Bukkit.getPlayer(announcerId)?.name ?: "Unknown"
            val guildDisplayName = GuildDisplayUtils.createGuildTag(guild)
            
            val formattedMessage = "§6[§l${guildDisplayName} ANNOUNCEMENT§r§6]§r\n" +
                    "§e$announcerName:§r $message"
            
            val recipients = getOnlineGuildMembers(guildId)
            val deliveredCount = broadcastMessageWithSound(recipients, formattedMessage, true)
            
            // Update rate limit
            updateAnnouncementRateLimit(announcerId)
            
            logger.info("Guild announcement from $announcerId delivered to $deliveredCount members of guild $guildId")
            return deliveredCount > 0
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error sending guild announcement", e)
            return false
        }
    }
    
    override fun sendGuildPing(guildId: UUID, pingerId: UUID, message: String?): Boolean {
        try {
            if (!canSendPings(pingerId, guildId)) {
                logger.warn("Player $pingerId cannot send pings for guild $guildId")
                return false
            }
            
            if (isPingRateLimited(pingerId)) {
                logger.warn("Player $pingerId is rate limited for pings")
                return false
            }
            
            val guild = guildService.getGuild(guildId)
            if (guild == null) {
                logger.warn("Guild $guildId not found")
                return false
            }
            
            val pingerName = Bukkit.getPlayer(pingerId)?.name ?: "Unknown"
            val guildDisplayName = GuildDisplayUtils.createGuildTag(guild)
            
            val formattedMessage = if (message != null) {
                "§c[§l${guildDisplayName} PING§r§c]§r §e$pingerName:§r $message"
            } else {
                "§c[§l${guildDisplayName} PING§r§c]§r §e$pingerName§r pinged the guild!"
            }
            
            val recipients = getOnlineGuildMembers(guildId)
            val deliveredCount = broadcastMessageWithSound(recipients, formattedMessage, true)
            
            // Update rate limit
            updatePingRateLimit(pingerId)
            
            logger.info("Guild ping from $pingerId delivered to $deliveredCount members of guild $guildId")
            return deliveredCount > 0
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error sending guild ping", e)
            return false
        }
    }
    
    override fun toggleChatVisibility(playerId: UUID, channel: ChatChannel): Boolean {
        try {
            val currentSettings = getVisibilitySettings(playerId)
            
            val newSettings = when (channel) {
                ChatChannel.GUILD -> currentSettings.copy(guildChatVisible = !currentSettings.guildChatVisible)
                ChatChannel.ALLY -> currentSettings.copy(allyChatVisible = !currentSettings.allyChatVisible)
                ChatChannel.PARTY -> currentSettings.copy(partyChatVisible = !currentSettings.partyChatVisible)
                ChatChannel.PUBLIC -> {
                    logger.warn("Cannot toggle visibility for public channel")
                    return false
                }
            }
            
            val success = updateVisibilitySettings(playerId, newSettings)
            
            if (success) {
                val visibilityState = when (channel) {
                    ChatChannel.GUILD -> newSettings.guildChatVisible
                    ChatChannel.ALLY -> newSettings.allyChatVisible
                    ChatChannel.PARTY -> newSettings.partyChatVisible
                    ChatChannel.PUBLIC -> false
                }
                logger.info("Player $playerId toggled $channel chat visibility to $visibilityState")
            }
            
            return success
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error toggling chat visibility", e)
            return false
        }
    }
    
    override fun getVisibilitySettings(playerId: UUID): ChatVisibilitySettings {
        return chatSettingsRepository.getVisibilitySettings(playerId)
    }
    
    override fun updateVisibilitySettings(playerId: UUID, settings: ChatVisibilitySettings): Boolean {
        return chatSettingsRepository.updateVisibilitySettings(settings)
    }
    
    override fun getRecipientsForChannel(senderId: UUID, channel: ChatChannel): Set<UUID> {
        return when (channel) {
            ChatChannel.GUILD -> {
                val senderGuilds = memberService.getPlayerGuilds(senderId)
                senderGuilds.flatMap { guildId ->
                    getOnlineGuildMembers(guildId).filter { playerId ->
                        getVisibilitySettings(playerId).guildChatVisible
                    }
                }.toSet()
            }
            ChatChannel.ALLY -> {
                val senderGuilds = memberService.getPlayerGuilds(senderId)
                senderGuilds.flatMap { guildId ->
                    getOnlineAlliedMembers(guildId).filter { playerId ->
                        getVisibilitySettings(playerId).allyChatVisible
                    }
                }.toSet()
            }
            ChatChannel.PARTY -> {
                // Get the sender's currently active party
                val activeParty = getCurrentActiveParty(senderId)
                if (activeParty == null) {
                    logger.debug("Player $senderId has no active party for messaging")
                    return emptySet()
                }

                // Get online members of the specific party
                val partyMembers = partyService.getOnlinePartyMembers(activeParty.id)
                partyMembers.filter { playerId ->
                    getVisibilitySettings(playerId).partyChatVisible
                }.toSet()
            }
            ChatChannel.PUBLIC -> {
                // Return all online players for public chat
                Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet()
            }
        }
    }
    
    override fun formatMessage(senderId: UUID, message: String, channel: ChatChannel): String {
        val senderName = Bukkit.getPlayer(senderId)?.name ?: "Unknown"
        val processedMessage = processEmojis(senderId, message)
        
        // Get sender's primary guild for context
        val senderGuilds = memberService.getPlayerGuilds(senderId)
        val primaryGuild = senderGuilds.firstOrNull()?.let { guildService.getGuild(it) }
        
        val guildTag = if (primaryGuild != null) {
            GuildDisplayUtils.createGuildTag(primaryGuild, brackets = false)
        } else {
            ""
        }
        
        return when (channel) {
            ChatChannel.GUILD -> {
                if (guildTag.isNotEmpty()) {
                    "§2[G] $guildTag §a$senderName:§r $processedMessage"
                } else {
                    "§2[G] §a$senderName:§r $processedMessage"
                }
            }
            ChatChannel.ALLY -> {
                if (guildTag.isNotEmpty()) {
                    "§3[A] $guildTag §b$senderName:§r $processedMessage"
                } else {
                    "§3[A] §b$senderName:§r $processedMessage"
                }
            }
            ChatChannel.PARTY -> {
                formatPartyMessage(senderId, senderName, processedMessage, guildTag)
            }
            ChatChannel.PUBLIC -> {
                if (guildTag.isNotEmpty()) {
                    "$guildTag §7$senderName:§r $processedMessage"
                } else {
                    "§7$senderName:§r $processedMessage"
                }
            }
        }
    }

    private fun formatPartyMessage(senderId: UUID, senderName: String, message: String, guildTag: String): String {
        try {
            val config = configService.loadConfig().party

            if (!config.partyChatEnabled) {
                // Fallback to basic formatting if disabled
                return if (guildTag.isNotEmpty()) {
                    "§d[P] $guildTag §d$senderName:§r $message"
                } else {
                    "§d[P] §d$senderName:§r $message"
                }
            }

            // Get the party name for the sender using the current active party
            val party = getCurrentActiveParty(senderId)
            val partyName = party?.name ?: "Party"

            // Get the player's rank status
            val playerGuilds = memberService.getPlayerGuilds(senderId)
            val primaryGuild = playerGuilds.firstOrNull()?.let { guildService.getGuild(it) }
            val playerRank = if (primaryGuild != null) {
                memberService.getMember(senderId, primaryGuild.id)?.rankId?.let { rankId ->
                    rankService.getRank(rankId)?.name ?: "Member"
                } ?: "Member"
            } else {
                "Member"
            }

            // Get LuckPerms suffix if available
            val luckPermsSuffix = getLuckPermsSuffix(senderId)

            // Build the format string with placeholders
            var formattedMessage = config.partyChatFormat
                .replace("%lumaguilds_party_name%", partyName)
                .replace("%lumaguilds_rel_<player>_status%", playerRank)
                .replace("%lumaguilds_guild_emoji%", primaryGuild?.emoji ?: "")
                .replace("%lumaguilds_guild_tag%", guildTag)
                .replace("%luckperms-suffix%", luckPermsSuffix)
                .replace("%player_name%", senderName)
                .replace("<message>", message)

            return formattedMessage
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error formatting party message", e)
            // Fallback to basic formatting
            return if (guildTag.isNotEmpty()) {
                "§d[P] $guildTag §d$senderName:§r $message"
            } else {
                "§d[P] §d$senderName:§r $message"
            }
        }
    }

    private fun getLuckPermsSuffix(playerId: UUID): String {
        // LuckPerms integration would require adding LuckPerms as a dependency
        // For now, return empty string - servers can use PlaceholderAPI or ChatControl
        // to handle LuckPerms placeholders in their chat format
        return ""
    }

    override fun canSendAnnouncements(playerId: UUID, guildId: UUID): Boolean {
        return memberService.hasPermission(playerId, guildId, RankPermission.SEND_ANNOUNCEMENTS)
    }
    
    override fun canSendPings(playerId: UUID, guildId: UUID): Boolean {
        return memberService.hasPermission(playerId, guildId, RankPermission.SEND_PINGS)
    }
    
    override fun isAnnouncementRateLimited(playerId: UUID): Boolean {
        val rateLimit = chatSettingsRepository.getRateLimit(playerId)
        val currentTime = System.currentTimeMillis()
        
        // Check time-based rate limit
        if (currentTime - rateLimit.lastAnnounceTime < announceRateLimit) {
            return true
        }
        
        // Check count-based rate limit (per hour)
        val oneHourAgo = currentTime - 3600000L
        if (rateLimit.lastAnnounceTime > oneHourAgo && rateLimit.announceCount >= maxAnnouncementsPerHour) {
            return true
        }
        
        return false
    }
    
    override fun isPingRateLimited(playerId: UUID): Boolean {
        val rateLimit = chatSettingsRepository.getRateLimit(playerId)
        val currentTime = System.currentTimeMillis()
        
        // Check time-based rate limit
        if (currentTime - rateLimit.lastPingTime < pingRateLimit) {
            return true
        }
        
        // Check count-based rate limit (per hour)
        val oneHourAgo = currentTime - 3600000L
        if (rateLimit.lastPingTime > oneHourAgo && rateLimit.pingCount >= maxPingsPerHour) {
            return true
        }
        
        return false
    }
    
    override fun processEmojis(senderId: UUID, message: String): String {
        val player = Bukkit.getPlayer(senderId) ?: return message
        
        // Simple emoji processing - find :emojiname: patterns and validate permissions
        val emojiPattern = Regex(":(\\w+):")
        
        return emojiPattern.replace(message) { matchResult ->
            val emojiPlaceholder = matchResult.value
            
            if (nexoEmojiService.hasEmojiPermission(player, emojiPlaceholder)) {
                emojiPlaceholder // Keep the placeholder for Nexo to process
            } else {
                matchResult.value // Return original text if no permission
            }
        }
    }
    
    override fun getOnlineGuildMembers(guildId: UUID): Set<UUID> {
        val members = memberService.getGuildMembers(guildId)
        return members.mapNotNull { member ->
            val player = Bukkit.getPlayer(member.playerId)
            if (player != null && player.isOnline) member.playerId else null
        }.toSet()
    }
    
    override fun getOnlineAlliedMembers(guildId: UUID): Set<UUID> {
        val allies = relationService.getGuildRelationsByType(guildId, RelationType.ALLY)
        val onlineMembers = mutableSetOf<UUID>()
        
        // Include own guild members
        onlineMembers.addAll(getOnlineGuildMembers(guildId))
        
        // Include allied guild members
        for (relation in allies) {
            val alliedGuildId = relation.getOtherGuild(guildId)
            onlineMembers.addAll(getOnlineGuildMembers(alliedGuildId))
        }
        
        return onlineMembers
    }
    
    override fun getOnlinePartyMembers(guildId: UUID): Set<UUID> {
        val parties = partyService.getActivePartiesForGuild(guildId)
        val onlineMembers = mutableSetOf<UUID>()
        
        for (party in parties) {
            onlineMembers.addAll(partyService.getOnlinePartyMembers(party.id))
        }
        
        return onlineMembers
    }
    
    override fun broadcastMessage(recipients: Set<UUID>, message: String): Int {
        var deliveredCount = 0

        for (playerId in recipients) {
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isOnline) {
                player.sendMessage(message)
                deliveredCount++
            }
        }

        return deliveredCount
    }

    private fun broadcastMessageWithPriority(recipients: Set<UUID>, message: String, priority: Int): Int {
        var deliveredCount = 0

        // For party chat, we send messages directly to avoid conflicts with other chat plugins
        // The priority setting helps determine message order if multiple chat systems are active
        for (recipientId in recipients) {
            val recipient = Bukkit.getPlayer(recipientId)
            if (recipient != null && recipient.isOnline) {
                // Send the message directly to bypass other chat plugins
                recipient.sendMessage(message)
                deliveredCount++

                // Optional: Play a subtle sound for party messages if enabled in config
                val config = configService.loadConfig().party
                if (config.partyChatEnabled) {
                    // Could add a subtle sound here if desired
                    // recipient.playSound(recipient.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 2.0f)
                }
            }
        }

        return deliveredCount
    }
    
    override fun broadcastMessageWithSound(recipients: Set<UUID>, message: String, soundNotification: Boolean): Int {
        var deliveredCount = 0
        
        for (playerId in recipients) {
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isOnline) {
                player.sendMessage(message)
                
                if (soundNotification) {
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.0f)
                }
                
                deliveredCount++
            }
        }
        
        return deliveredCount
    }
    
    private fun updateAnnouncementRateLimit(playerId: UUID) {
        val currentRateLimit = chatSettingsRepository.getRateLimit(playerId)
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - 3600000L
        
        // Reset count if it's been more than an hour
        val newCount = if (currentRateLimit.lastAnnounceTime < oneHourAgo) {
            1
        } else {
            currentRateLimit.announceCount + 1
        }
        
        val updatedRateLimit = currentRateLimit.copy(
            lastAnnounceTime = currentTime,
            announceCount = newCount
        )
        
        chatSettingsRepository.updateRateLimit(updatedRateLimit)
    }
    
    private fun updatePingRateLimit(playerId: UUID) {
        val currentRateLimit = chatSettingsRepository.getRateLimit(playerId)
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - 3600000L
        
        // Reset count if it's been more than an hour
        val newCount = if (currentRateLimit.lastPingTime < oneHourAgo) {
            1
        } else {
            currentRateLimit.pingCount + 1
        }
        
        val updatedRateLimit = currentRateLimit.copy(
            lastPingTime = currentTime,
            pingCount = newCount
        )
        
        chatSettingsRepository.updateRateLimit(updatedRateLimit)
    }

    /**
     * Gets the player's currently active party for messaging.
     * Only returns a party if the player has explicitly switched to one.
     * Players are in GLOBAL chat by default (no automatic party assignment).
     */
    private fun getCurrentActiveParty(playerId: UUID): net.lumalyte.lg.domain.entities.Party? {
        // Check if player has explicitly switched to a party
        val preference = preferenceRepository.getByPlayerId(playerId)
        if (preference != null) {
            val party = partyRepository.getById(preference.partyId)
            if (party != null && party.isActive()) {
                return party
            } else {
                // Party no longer exists or is inactive, remove the preference
                preferenceRepository.removeByPlayerId(playerId)
            }
        }

        // No automatic party assignment - players are in GLOBAL chat by default
        return null
    }
}
