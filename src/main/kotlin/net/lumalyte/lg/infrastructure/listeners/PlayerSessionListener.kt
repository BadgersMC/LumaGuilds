package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.infrastructure.services.TeleportationService
import net.lumalyte.lg.infrastructure.services.VaultHologramService
import net.lumalyte.lg.interaction.listeners.GuildChatListener
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Listens to player session events (join, quit) and performs cleanup.
 */
class PlayerSessionListener : Listener, KoinComponent {

    private val teleportationService: TeleportationService by inject()
    private val hologramService: VaultHologramService by inject()
    private val vaultInventoryManager: VaultInventoryManager by inject()
    private val guildChatListener: GuildChatListener by inject()

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
