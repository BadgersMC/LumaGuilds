package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.slf4j.LoggerFactory

/**
 * Listener that handles cleanup of admin override state when players disconnect.
 *
 * This ensures that override mode is properly cleared when an admin logs out,
 * preventing state leakage between sessions.
 */
class AdminOverrideListener(
    private val adminOverrideService: AdminOverrideService,
    private val guildRolePermissionResolver: GuildRolePermissionResolver
) : Listener {

    private val logger = LoggerFactory.getLogger(AdminOverrideListener::class.java)

    /**
     * Handles player quit events to clear admin override state.
     *
     * When a player with active override disconnects:
     * 1. Clears their override state
     * 2. Invalidates their permission cache
     *
     * This ensures override mode doesn't persist across login sessions.
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId

        try {
            // Check if player has override enabled
            if (adminOverrideService.hasOverride(playerId)) {
                // Clear the override state
                adminOverrideService.clearOverride(playerId)

                // Invalidate the player's permission cache
                guildRolePermissionResolver.invalidatePlayerCache(playerId)

                logger.debug("Cleared admin override for player {} on logout", playerId)
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.warn("Error clearing admin override for player {} on logout", playerId, e)

            // Still attempt to invalidate cache even if clearOverride failed
            try {
                guildRolePermissionResolver.invalidatePlayerCache(playerId)
            } catch (cacheException: Exception) {
                logger.warn("Error invalidating cache for player {} on logout", playerId, cacheException)
            }
        }
    }
}
