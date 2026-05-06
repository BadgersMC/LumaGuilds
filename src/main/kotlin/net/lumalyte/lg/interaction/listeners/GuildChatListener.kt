package net.lumalyte.lg.interaction.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.values.ChatChannel
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener that intercepts regular chat messages and routes them to guild chat
 * when a player has toggled guild chat mode on with /g chat.
 *
 * IMPORTANT: This listener ONLY intercepts messages when:
 * - Player has used /g chat to enable guild chat mode
 * - Player is currently a member of a guild
 *
 * EVENT PRIORITY: Registered at LOWEST so it fires first. We clear event.viewers()
 * in addition to cancelling the event. This prevents RoseChat (HIGHEST priority,
 * ignoreCancelled=false) from re-broadcasting to main chat — it iterates over the
 * viewer set which is already empty by the time it runs.
 *
 * GLOBAL chat behavior:
 * - Players with guild chat mode OFF = main chat (default)
 * - Players with guild chat mode ON = guild chat only
 */
class GuildChatListener : Listener, KoinComponent {

    private val chatService: ChatService by inject()
    private val guildService: GuildService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val plugin: Plugin by inject()

    /** Marker read by RoseChat (and other forks) to skip its pipeline when we've claimed the chat. */
    private val chatClaimMeta = "lumaguilds:chat_claimed"

    private val logger = LoggerFactory.getLogger(GuildChatListener::class.java)

    /** Set of player UUIDs currently in guild chat mode. In-memory only — resets on server restart. */
    private val guildChatPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player

        // Skip if player is in GUI input mode (ChatInputListener)
        if (chatInputListener.isInInputMode(player)) return

        val playerId = player.uniqueId

        // Only intercept if player has guild chat mode enabled
        if (!guildChatPlayers.contains(playerId)) {
            // Defensive: ensure stale claim metadata never survives a toggle-off.
            // Without this, RoseChat sees an old `lumaguilds:chat_claimed` marker
            // and silently drops the first vanilla message after disabling /g chat.
            if (player.hasMetadata(chatClaimMeta)) {
                player.removeMetadata(chatClaimMeta, plugin)
            }
            return
        }

        // Claim the event immediately so RoseChat (and any other legacy
        // AsyncPlayerChatEvent listener) skips its pipeline. Must happen
        // BEFORE any validation that can early-return, otherwise a transient
        // empty-guild result leaks the message to global.
        event.isCancelled = true
        event.viewers().clear()
        player.setMetadata(chatClaimMeta, FixedMetadataValue(plugin, true))

        // Verify player is still in a guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            // Player left their guild — auto-disable guild chat mode
            guildChatPlayers.remove(playerId)
            player.sendMessage("§c❌ Guild chat disabled — you are no longer in a guild.")
            return
        }

        // Extract plain text from the Adventure component
        val message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        if (message.isEmpty()) return

        // Route to guild chat
        val success = chatService.routeMessage(playerId, message, ChatChannel.GUILD)
        if (!success) {
            logger.warn("Failed to route guild chat message from player $playerId")
            player.sendMessage("§c❌ Failed to send guild message. Are you in a guild?")
        }
    }

    /**
     * Toggles guild chat mode for a player.
     * @return true if guild chat is now ON, false if now OFF.
     */
    fun toggleGuildChat(player: Player): Boolean {
        val playerId = player.uniqueId
        return if (guildChatPlayers.remove(playerId)) {
            // Clear claim metadata so RoseChat resumes normal handling for this player.
            if (player.hasMetadata(chatClaimMeta)) {
                player.removeMetadata(chatClaimMeta, plugin)
            }
            false // was on, now off
        } else {
            guildChatPlayers.add(playerId)
            true // was off, now on
        }
    }

    fun isInGuildChatMode(playerId: UUID): Boolean = guildChatPlayers.contains(playerId)

    /** Called when a player quits or is removed from a guild. */
    fun removePlayer(playerId: UUID) {
        guildChatPlayers.remove(playerId)
        org.bukkit.Bukkit.getPlayer(playerId)?.let { p ->
            if (p.hasMetadata(chatClaimMeta)) p.removeMetadata(chatClaimMeta, plugin)
        }
    }
}
