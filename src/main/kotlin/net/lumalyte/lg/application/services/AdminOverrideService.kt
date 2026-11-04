package net.lumalyte.lg.application.services

import java.util.UUID

/**
 * Service interface for managing admin override state.
 *
 * This service provides administrators with the ability to bypass guild membership
 * and permission restrictions. When override is enabled, administrators can:
 * - Inspect another guild without joining it
 * - Exercise explicitly authorized administration operations with bypass enabled
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

    /** Set the transient guild inspection context; never changes membership. */
    fun setViewedGuild(playerId: UUID, guildId: UUID)

    /** Get the guild currently inspected by this administrator, if bypass is active. */
    fun getViewedGuild(playerId: UUID): UUID?

    /** Clear the transient inspection context without changing guild membership. */
    fun clearViewedGuild(playerId: UUID)

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
