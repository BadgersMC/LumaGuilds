package net.lumalyte.lg.interaction.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.badgersmc.nexus.i18n.LangService
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
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Explicit lazy collaborators for [PartyChatListener] (REQ-NEXUS-DI-1..3); each resolves on first use. */
data class PartyChatListenerDependencies(
    val chatService: Lazy<ChatService>,
    val preferenceRepository: Lazy<PlayerPartyPreferenceRepository>,
    val partyRepository: Lazy<PartyRepository>,
    val guildService: Lazy<GuildService>,
    val memberService: Lazy<MemberService>,
    val configService: Lazy<ConfigService>,
    val chatInputListener: Lazy<ChatInputListener>,
    val plugin: Lazy<Plugin>,
    val lang: Lazy<LangService>,
)

/**
 * Listener that intercepts regular chat messages and routes them to party chat
 * when a player has switched to a specific party using /pc switch
 *
 * IMPORTANT: This listener ONLY intercepts messages when:
 * - Player has explicitly used /pc switch <partyname> to join a party
 * - Player has a stored party preference in the database
 *
 * GLOBAL chat behavior:
 * - Players with NO party preference = GLOBAL chat (default)
 * - This listener does NOT intercept GLOBAL messages (returns early at line 55)
 * - GLOBAL messages are handled by ChatControl or vanilla Minecraft
 * - Party formatting ONLY applies to party messages, never to GLOBAL
 *
 * Note: This listener does NOT use @EventHandler annotation - it is manually
 * registered in LumaGuilds.kt with a configurable priority from config.yml
 */
class PartyChatListener(
    dependencies: PartyChatListenerDependencies,
) : Listener {

    private val chatService: ChatService by dependencies.chatService
    private val preferenceRepository: PlayerPartyPreferenceRepository by dependencies.preferenceRepository
    private val partyRepository: PartyRepository by dependencies.partyRepository
    private val guildService: GuildService by dependencies.guildService
    private val memberService: MemberService by dependencies.memberService
    private val configService: ConfigService by dependencies.configService
    private val chatInputListener: ChatInputListener by dependencies.chatInputListener
    private val plugin: Plugin by dependencies.plugin
    private val lang: LangService by dependencies.lang

    private val chatClaimMeta = "lumaguilds:chat_claimed"

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
        // IMPORTANT: If no preference exists, player is in GLOBAL chat and we return immediately
        // This means GLOBAL messages are NEVER intercepted or formatted by this listener
        val playerId = player.uniqueId
        val preference = preferenceRepository.getByPlayerId(playerId) ?: return // GLOBAL chat - don't intercept

        // Cancel, clear viewers, and set the chat-claim metadata marker so RoseChat's
        // legacy AsyncPlayerChatEvent listener skips its pipeline.
        event.isCancelled = true
        event.viewers().clear()
        player.setMetadata(chatClaimMeta, FixedMetadataValue(plugin, true))

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
            player.sendMessage(lang.msg("party.chat.no_permission"))
            player.sendMessage(lang.msg("party.chat.return_global"))
            return // Event already cancelled at line 71
        }

        // Check if player is banned
        if (party.isPlayerBanned(playerId)) {
            player.sendMessage(lang.msg("party.chat.banned"))
            player.sendMessage(lang.msg("party.chat.return_global"))
            return // Event already cancelled at line 71
        }

        // Check if player is muted
        if (party.isPlayerMuted(playerId)) {
            val muteExpiration = party.mutedPlayers[playerId]
            if (muteExpiration != null) {
                // Temporary mute - show remaining time
                val remaining = Duration.between(Instant.now(), muteExpiration)
                val hours = remaining.toHours()
                val minutes = remaining.toMinutes() % 60
                player.sendMessage(lang.msg("party.chat.muted_temporary"))
                player.sendMessage(lang.msg("party.chat.time_remaining", "hours" to hours, "minutes" to minutes))
            } else {
                // Permanent mute
                player.sendMessage(lang.msg("party.chat.muted_permanent"))
            }
            player.sendMessage(lang.msg("party.chat.return_global"))
            return // Event already cancelled at line 71
        }

        // Convert Adventure Component to plain text
        val plainTextSerializer = PlainTextComponentSerializer.plainText()
        val message = plainTextSerializer.serialize(event.message()).trim()

        // Route the message through the chat service
        val success = chatService.routeMessage(playerId, message, ChatChannel.PARTY)

        if (!success) {
            player.sendMessage(lang.msg("party.chat.send_failed"))
        }
    }
}
