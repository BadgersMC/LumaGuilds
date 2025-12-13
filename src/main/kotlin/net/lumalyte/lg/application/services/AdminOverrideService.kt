package net.lumalyte.lg.application.services

import java.util.UUID

/**
 * Service interface for managing admin override state.
 *
 * This service provides administrators with the ability to bypass guild membership
 * and permission restrictions. When override is enabled, administrators can:
 * - Join any guild without invitations or membership checks
 * - Have owner-level permissions in all guild claims
 *
 * Override state is session-only and automatically cleared on logout.
 */
interface AdminOverrideService {

    /**
     * Toggle override state for a player.
     * If currently enabled, it will be disabled, and vice versa.
     *
     * @param playerId The UUID of the player to toggle override for.
     * @return true if override is now enabled, false if now disabled.
     */
    fun toggleOverride(playerId: UUID): Boolean

    /**
     * Check if a player has override enabled.
     *
     * @param playerId The UUID of the player to check.
     * @return true if the player has override enabled, false otherwise.
     */
    fun hasOverride(playerId: UUID): Boolean

    /**
     * Enable override for a player.
     *
     * @param playerId The UUID of the player to enable override for.
     */
    fun enableOverride(playerId: UUID)

    /**
     * Disable override for a player.
     *
     * @param playerId The UUID of the player to disable override for.
     */
    fun disableOverride(playerId: UUID)

    /**
     * Clear override state for a player.
     * This is typically called when a player logs out.
     *
     * @param playerId The UUID of the player to clear override state for.
     */
    fun clearOverride(playerId: UUID)
}
