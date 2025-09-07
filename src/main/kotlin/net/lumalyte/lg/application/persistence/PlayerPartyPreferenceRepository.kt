package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.PlayerPartyPreference
import java.util.UUID

/**
 * Repository interface for managing player party preferences.
 */
interface PlayerPartyPreferenceRepository {

    /**
     * Gets the party preference for a player.
     *
     * @param playerId The ID of the player.
     * @return The player's party preference, or null if none exists.
     */
    fun getByPlayerId(playerId: UUID): PlayerPartyPreference?

    /**
     * Sets or updates a player's party preference.
     *
     * @param preference The player party preference to save.
     * @return true if successful, false otherwise.
     */
    fun save(preference: PlayerPartyPreference): Boolean

    /**
     * Removes a player's party preference.
     *
     * @param playerId The ID of the player.
     * @return true if successful, false otherwise.
     */
    fun removeByPlayerId(playerId: UUID): Boolean

    /**
     * Gets all player party preferences.
     *
     * @return A set of all player party preferences.
     */
    fun getAll(): Set<PlayerPartyPreference>

    /**
     * Removes preferences for parties that no longer exist.
     *
     * @param validPartyIds Set of party IDs that still exist.
     * @return The number of preferences removed.
     */
    fun removeInvalidPreferences(validPartyIds: Set<UUID>): Int
}
