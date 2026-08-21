package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Utility class for handling chat-related functionality.
 * Uses Unicode 1.1 compatible emojis directly.
 */
object ChatUtils {

    /**
     * Sends a message to a player.
     * Uses Unicode 1.1 compatible emojis directly.
     *
     * @param player The player to send the message to
     * @param message The message to send (with Unicode 1.1 emojis)
     */
    fun sendMessage(player: Player, message: String) {
        player.sendMessage(message)
    }

    /**
     * Broadcasts a message to all online players.
     * Uses Unicode 1.1 compatible emojis directly.
     *
     * @param message The message to broadcast
     * @param excludePlayer Optional player to exclude from the broadcast
     */
    fun broadcastMessage(message: String, excludePlayer: Player? = null) {
        val server = org.bukkit.Bukkit.getServer()

        for (onlinePlayer in server.onlinePlayers) {
            if (onlinePlayer != excludePlayer) {
                onlinePlayer.sendMessage(message)
            }
        }
    }

    /** Broadcasts an Adventure component without flattening formatting or events. */
    fun broadcastMessage(message: Component, excludePlayer: Player? = null) {
        val server = org.bukkit.Bukkit.getServer()
        for (onlinePlayer in server.onlinePlayers) {
            if (onlinePlayer != excludePlayer) {
                onlinePlayer.sendMessage(message)
            }
        }
    }

}
