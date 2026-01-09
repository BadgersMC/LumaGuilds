package net.lumalyte.lg.infrastructure.services.apollo

import com.lunarclient.apollo.Apollo
import com.lunarclient.apollo.player.ApolloPlayer
import net.lumalyte.lg.application.services.apollo.LunarClientService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Bukkit implementation of LunarClientService.
 * Manages detection and caching of Lunar Client players using Apollo API.
 */
class LunarClientServiceBukkit : LunarClientService {

    private val logger = LoggerFactory.getLogger(LunarClientServiceBukkit::class.java)

    // Cache Apollo player instances for performance
    private val apolloPlayerCache = ConcurrentHashMap<UUID, ApolloPlayer>()

    // Track Apollo availability
    private val apolloAvailable: Boolean by lazy {
        try {
            Bukkit.getPluginManager().getPlugin("Apollo-Bukkit") != null
        } catch (e: Exception) {
            logger.warn("Failed to check Apollo availability: ${e.message}")
            false
        }
    }

    override fun isLunarClient(player: Player): Boolean {
        if (!apolloAvailable) return false

        return try {
            Apollo.getPlayerManager().hasSupport(player.uniqueId)
        } catch (e: Exception) {
            logger.debug("Error checking Lunar Client status for ${player.name}: ${e.message}")
            false
        }
    }

    override fun getApolloPlayer(player: Player): ApolloPlayer? {
        if (!apolloAvailable) return null
        if (!isLunarClient(player)) return null

        // Return cached instance if available
        val cached = apolloPlayerCache[player.uniqueId]
        if (cached != null) return cached

        // Get from Apollo and cache
        return try {
            val apolloPlayer = Apollo.getPlayerManager().getPlayer(player.uniqueId).orElse(null)
            if (apolloPlayer != null) {
                apolloPlayerCache[player.uniqueId] = apolloPlayer
            }
            apolloPlayer
        } catch (e: Exception) {
            logger.debug("Error getting ApolloPlayer for ${player.name}: ${e.message}")
            null
        }
    }

    override fun getLunarClientPlayers(): Collection<ApolloPlayer> {
        if (!apolloAvailable) return emptyList()

        return try {
            Apollo.getPlayerManager().players
        } catch (e: Exception) {
            logger.warn("Error getting Lunar Client players: ${e.message}")
            emptyList()
        }
    }

    override fun isApolloAvailable(): Boolean {
        return apolloAvailable
    }

    override fun getLunarClientCount(): Int {
        if (!apolloAvailable) return 0

        return try {
            Apollo.getPlayerManager().players.size
        } catch (e: Exception) {
            logger.debug("Error getting Lunar Client count: ${e.message}")
            0
        }
    }

    /**
     * Clear cache entry for a player (call on player quit).
     */
    fun clearPlayerCache(playerId: UUID) {
        apolloPlayerCache.remove(playerId)
    }

    /**
     * Clear all cached Apollo players.
     */
    fun clearCache() {
        apolloPlayerCache.clear()
    }

    /**
     * Get cache size for debugging.
     */
    fun getCacheSize(): Int {
        return apolloPlayerCache.size
    }
}
