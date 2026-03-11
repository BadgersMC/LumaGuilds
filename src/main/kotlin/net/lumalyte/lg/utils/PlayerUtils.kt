package net.lumalyte.lg.utils

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Finds a player by name, handling Floodgate's dot-prefix for Bedrock players.
 *
 * On Geyser/Floodgate servers, Bedrock players are registered under a prefixed name
 * (e.g., ".PlayerName"). This function first tries the plain name, then retries with
 * the Floodgate prefix so callers don't need to know whether a target is Java or Bedrock.
 *
 * @param playerName The name to search for (with or without Floodgate prefix).
 * @return The online [Player], or `null` if not found.
 */
fun findPlayerByName(playerName: String): Player? {
    // Try normal lookup first (handles Java players and Bedrock names typed with the prefix)
    Bukkit.getPlayer(playerName)?.let { return it }

    // Retry with the Floodgate prefix so callers can type plain Bedrock usernames
    return try {
        val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
        Bukkit.getPlayer("${floodgateApi.playerPrefix}$playerName")
    } catch (e: Exception) {
        // Floodgate not available or player genuinely not found
        null
    }
}
