package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.InventoryView
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

/**
 * Platform service for player interactions.
 * Provides core player-related operations that depend on the game engine.
 */
interface PlayerService {
    /**
     * Sends a message to a player
     */
    fun sendMessage(playerId: UUID, message: String)

    /**
     * Gets a player's display name
     */
    fun getPlayerName(playerId: UUID): String?

    /**
     * Checks if a player is currently online
     */
    fun isPlayerOnline(playerId: UUID): Boolean

    /**
     * Gets all currently online player UUIDs
     */
    fun getOnlinePlayers(): List<UUID>

    /**
     * Checks if a player has a specific permission
     */
    fun hasPermission(playerId: UUID, permission: String): Boolean

    /**
     * Gets a player's inventory as an InventoryView
     */
    fun getPlayerInventory(playerId: UUID): InventoryView?

    /**
     * Opens an inventory GUI for a player
     */
    fun openInventory(playerId: UUID, inventory: InventoryView)

    /**
     * Closes a player's currently open inventory
     */
    fun closeInventory(playerId: UUID)

    /**
     * Gets a player's current position
     */
    fun getPlayerPosition(playerId: UUID): Position3D?

    /**
     * Gets the world UUID the player is currently in
     */
    fun getPlayerWorld(playerId: UUID): UUID?

    /**
     * Plays a sound for a specific player
     */
    fun playSound(playerId: UUID, sound: String, volume: Float = 1.0f, pitch: Float = 1.0f)
}
