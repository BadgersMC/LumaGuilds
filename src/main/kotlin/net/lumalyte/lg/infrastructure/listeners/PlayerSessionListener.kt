package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.infrastructure.services.TeleportationService
import net.lumalyte.lg.infrastructure.services.VaultHologramService
import net.lumalyte.lg.application.services.GuildLoginNotificationService
import net.lumalyte.lg.application.services.QuestCompletionNotifier
import net.lumalyte.lg.application.services.WarNotificationService
import net.lumalyte.lg.interaction.listeners.GuildChatListener
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/** Explicit lazy collaborators for [PlayerSessionListener] (REQ-NEXUS-DI-1..3); each resolves on first use. */
data class PlayerSessionListenerDependencies(
    val teleportationService: Lazy<TeleportationService>,
    val hologramService: Lazy<VaultHologramService>,
    val vaultInventoryManager: Lazy<VaultInventoryManager>,
    val guildChatListener: Lazy<GuildChatListener>,
    val warNotificationService: Lazy<WarNotificationService>,
    val questCompletionNotifier: Lazy<QuestCompletionNotifier>,
    val guildLoginNotificationService: Lazy<GuildLoginNotificationService>,
    val questAvailabilityNotifier: Lazy<net.lumalyte.lg.infrastructure.services.QuestAvailabilityNotifierBukkit>,
)

/**
 * Listens to player session events (join, quit) and performs cleanup.
 */
class PlayerSessionListener(
    dependencies: PlayerSessionListenerDependencies,
) : Listener {

    private val teleportationService: TeleportationService by dependencies.teleportationService
    private val hologramService: VaultHologramService by dependencies.hologramService
    private val vaultInventoryManager: VaultInventoryManager by dependencies.vaultInventoryManager
    private val guildChatListener: GuildChatListener by dependencies.guildChatListener
    private val warNotificationService: WarNotificationService by dependencies.warNotificationService
    private val questCompletionNotifier: QuestCompletionNotifier by dependencies.questCompletionNotifier
    private val guildLoginNotificationService: GuildLoginNotificationService by dependencies.guildLoginNotificationService
    private val questAvailabilityNotifier: net.lumalyte.lg.infrastructure.services.QuestAvailabilityNotifierBukkit by dependencies.questAvailabilityNotifier

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerId = event.player.uniqueId
        warNotificationService.deliverUnread(playerId)
        questCompletionNotifier.deliverUnread(playerId)
        guildLoginNotificationService.onPlayerJoin(playerId)
        questAvailabilityNotifier.onJoin(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Cleanup any active teleports
        teleportationService.onPlayerQuit(player.uniqueId)

        // Cleanup hologram visibility tracking
        hologramService.onPlayerQuit(player)

        // Cleanup vault viewer sessions to prevent memory leaks
        vaultInventoryManager.cleanupDisconnectedPlayer(player.uniqueId)

        // Remove guild chat mode so the in-memory set doesn't leak on reconnect
        guildChatListener.removePlayer(player.uniqueId)
    }
}
