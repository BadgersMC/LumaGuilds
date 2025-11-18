package net.lumalyte.lg.domain.entities

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

/**
 * Represents an active player viewing session for a guild vault.
 * Tracks which player is viewing which vault and their Bukkit inventory reference.
 */
data class ViewerSession(
    /**
     * The UUID of the player viewing the vault.
     */
    val playerId: UUID,

    /**
     * The UUID of the guild whose vault is being viewed.
     */
    val guildId: UUID,

    /**
     * Reference to the Bukkit Inventory being displayed to the player.
     * Used for real-time updates when other players modify the vault.
     */
    val inventory: Inventory,

    /**
     * Timestamp when the player opened the vault.
     */
    val openedTimestamp: Long = System.currentTimeMillis(),

    /**
     * Timestamp of the last interaction (click, item move, etc.).
     */
    var lastInteractionTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Updates the last interaction timestamp to the current time.
     */
    fun recordInteraction() {
        lastInteractionTimestamp = System.currentTimeMillis()
    }

    /**
     * Gets the duration (in milliseconds) that this vault has been open.
     */
    fun getSessionDuration(): Long {
        return System.currentTimeMillis() - openedTimestamp
    }

    /**
     * Gets the time (in milliseconds) since the last interaction.
     */
    fun getTimeSinceLastInteraction(): Long {
        return System.currentTimeMillis() - lastInteractionTimestamp
    }

    /**
     * Checks if this session has been idle for longer than the specified threshold.
     *
     * @param thresholdMs The idle threshold in milliseconds.
     * @return true if the session has been idle for longer than the threshold.
     */
    fun isIdle(thresholdMs: Long = 300000): Boolean {
        return getTimeSinceLastInteraction() > thresholdMs
    }
}
