package net.lumalyte.lg.application.services.apollo

import com.lunarclient.apollo.player.ApolloPlayer
import org.bukkit.entity.Player
import java.util.*

/**
 * Service interface for detecting and managing Lunar Client players.
 * Provides utilities for Apollo API integration.
 */
interface LunarClientService {
    /**
     * Check if a player is using Lunar Client.
     *
     * @param player The player to check
     * @return true if the player is using Lunar Client, false otherwise
     */
    fun isLunarClient(player: Player): Boolean

    /**
     * Get an ApolloPlayer instance for a Bukkit player.
     *
     * @param player The Bukkit player
     * @return Optional ApolloPlayer if the player is using Lunar Client
     */
    fun getApolloPlayer(player: Player): ApolloPlayer?

    /**
     * Get all online Lunar Client players.
     *
     * @return Collection of all ApolloPlayer instances
     */
    fun getLunarClientPlayers(): Collection<ApolloPlayer>

    /**
     * Check if Apollo API is available on the server.
     *
     * @return true if Apollo-Bukkit plugin is loaded
     */
    fun isApolloAvailable(): Boolean

    /**
     * Get the number of online Lunar Client players.
     *
     * @return Count of LC players
     */
    fun getLunarClientCount(): Int
}
