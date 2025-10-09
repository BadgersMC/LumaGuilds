package net.lumalyte.lg.utils

import org.bukkit.Bukkit
import java.util.UUID

/**
 * Utility class for player-related operations.
 */
object PlayerUtils {

    /**
     * Gets the name of a player from their UUID.
     *
     * @param playerId The UUID of the player
     * @return The player's name, or "Unknown Player" if not found
     */
    fun getPlayerName(playerId: UUID): String {
        return Bukkit.getOfflinePlayer(playerId).name ?: "Unknown Player"
    }

    /**
     * Gets the name of a player from their UUID with a custom fallback.
     *
     * @param playerId The UUID of the player
     * @param fallback The fallback name if player is not found
     * @return The player's name or the fallback
     */
    fun getPlayerName(playerId: UUID, fallback: String): String {
        return Bukkit.getOfflinePlayer(playerId).name ?: fallback
    }
}
