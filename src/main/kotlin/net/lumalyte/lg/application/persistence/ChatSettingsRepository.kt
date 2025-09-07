package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.values.ChatVisibilitySettings
import net.lumalyte.lg.domain.values.ChatRateLimit
import java.util.UUID

/**
 * Repository interface for managing chat settings and rate limiting persistence.
 */
interface ChatSettingsRepository {
    
    /**
     * Gets the chat visibility settings for a player.
     *
     * @param playerId The ID of the player.
     * @return The chat visibility settings, or default settings if not found.
     */
    fun getVisibilitySettings(playerId: UUID): ChatVisibilitySettings
    
    /**
     * Updates the chat visibility settings for a player.
     *
     * @param settings The visibility settings to update.
     * @return true if the settings were updated successfully, false otherwise.
     */
    fun updateVisibilitySettings(settings: ChatVisibilitySettings): Boolean
    
    /**
     * Gets the rate limit information for a player.
     *
     * @param playerId The ID of the player.
     * @return The rate limit information, or default if not found.
     */
    fun getRateLimit(playerId: UUID): ChatRateLimit
    
    /**
     * Updates the rate limit information for a player.
     *
     * @param rateLimit The rate limit information to update.
     * @return true if the rate limit was updated successfully, false otherwise.
     */
    fun updateRateLimit(rateLimit: ChatRateLimit): Boolean
    
    /**
     * Resets rate limit counters for a player.
     *
     * @param playerId The ID of the player.
     * @return true if the reset was successful, false otherwise.
     */
    fun resetRateLimit(playerId: UUID): Boolean
    
    /**
     * Gets all players who have customized visibility settings.
     *
     * @return A set of player IDs with custom settings.
     */
    fun getPlayersWithCustomSettings(): Set<UUID>
    
    /**
     * Removes all chat settings for a player (cleanup on leave/ban).
     *
     * @param playerId The ID of the player.
     * @return true if the cleanup was successful, false otherwise.
     */
    fun removePlayerSettings(playerId: UUID): Boolean
}
