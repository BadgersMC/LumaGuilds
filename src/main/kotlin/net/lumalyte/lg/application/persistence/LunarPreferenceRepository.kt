package net.lumalyte.lg.application.persistence

import java.util.UUID

/**
 * Persists Lunar Client visibility preferences.
 *
 * Two independent settings:
 * - Per-player team tracking opt-in. Absence of a row means opted OUT (default).
 *   Players individually consent to broadcasting their location to guild teammates.
 * - Per-guild waypoint visibility. Absence of a row means visible (default).
 *   Guild owners/admins can hide guild home waypoints from teammates' minimaps.
 */
interface LunarPreferenceRepository {
    fun isPlayerTeamTrackingEnabled(playerId: UUID): Boolean
    fun setPlayerTeamTrackingEnabled(playerId: UUID, enabled: Boolean): Boolean
    fun getOptedInPlayerIds(): Set<UUID>

    fun isGuildWaypointsVisible(guildId: UUID): Boolean
    fun setGuildWaypointsVisible(guildId: UUID, visible: Boolean): Boolean
}
