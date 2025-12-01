package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.infrastructure.services.TeleportationService
import net.lumalyte.lg.infrastructure.services.VaultHologramService
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

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Cleanup any active teleports
        teleportationService.onPlayerQuit(player.uniqueId)

        // Cleanup hologram visibility tracking
        hologramService.onPlayerQuit(player)

        // Cleanup vault viewer sessions to prevent memory leaks
        vaultInventoryManager.cleanupDisconnectedPlayer(player.uniqueId)
    }
}
