package net.lumalyte.lg.interaction.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Listener that intercepts regular chat messages and routes them to party chat
 * when a player has switched to a specific party using /pc switch
 *
 * Note: This listener does NOT use @EventHandler annotation - it is manually
 * registered in LumaGuilds.kt with a configurable priority from config.yml
 */
class PartyChatListener : Listener, KoinComponent {

    private val chatService: ChatService by inject()
    private val preferenceRepository: PlayerPartyPreferenceRepository by inject()
    private val partyRepository: PartyRepository by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val chatInputListener: ChatInputListener by inject()

    private val logger = LoggerFactory.getLogger(PartyChatListener::class.java)

    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player

        // Skip if player is in GUI input mode (ChatInputListener)
        if (chatInputListener.isInInputMode(player)) {
            return
        }

        // Check if parties are enabled
        if (!configService.loadConfig().partiesEnabled) {
            return
        }

        // Check if player has an active party preference
        val playerId = player.uniqueId
        val preference = preferenceRepository.getByPlayerId(playerId) ?: return

        // Get the party
        val party = partyRepository.getById(preference.partyId)
        if (party == null || !party.isActive()) {
            // Party no longer exists or is inactive - remove preference
            preferenceRepository.removeByPlayerId(playerId)
            return
        }

        // Validate player can still access this party
        val playerGuildIds = guildService.getPlayerGuilds(playerId).map { it.id }.toSet()
        if (playerGuildIds.isEmpty()) {
            return
        }

        val playerGuildId = playerGuildIds.firstOrNull() ?: return
        val playerRankId = memberService.getMember(playerId, playerGuildId)?.rankId

        // Check role restrictions
        if (playerRankId != null && !party.canPlayerJoin(playerRankId)) {
            player.sendMessage("§c❌ You don't have permission to chat in this party!")
            player.sendMessage("§7Use §f/pc switch GLOBAL §7to return to global chat")
            event.isCancelled = true
            return
        }

        // Check if player is banned
        if (party.isPlayerBanned(playerId)) {
            player.sendMessage("§c❌ You are banned from this channel!")
            player.sendMessage("§7Use §f/pc switch GLOBAL §7to return to global chat")
            event.isCancelled = true
            return
        }

        // Check if player is muted
        if (party.isPlayerMuted(playerId)) {
            val muteExpiration = party.mutedPlayers[playerId]
            if (muteExpiration != null) {
                // Temporary mute - show remaining time
                val remaining = Duration.between(Instant.now(), muteExpiration)
                val hours = remaining.toHours()
                val minutes = remaining.toMinutes() % 60
                player.sendMessage("§c❌ You are muted in this channel!")
                player.sendMessage("§7Time remaining: §f${hours}h ${minutes}m")
            } else {
                // Permanent mute
                player.sendMessage("§c❌ You are permanently muted in this channel!")
            }
            player.sendMessage("§7Use §f/pc switch GLOBAL §7to return to global chat")
            event.isCancelled = true
            return
        }

        // Cancel the default chat event
        event.isCancelled = true

        // Convert Adventure Component to plain text
        val plainTextSerializer = PlainTextComponentSerializer.plainText()
        val message = plainTextSerializer.serialize(event.message()).trim()

        // Route the message through the chat service
        val success = chatService.routeMessage(playerId, message, ChatChannel.PARTY)

        if (!success) {
            player.sendMessage("§c❌ Failed to send party message!")
        }
    }
}
