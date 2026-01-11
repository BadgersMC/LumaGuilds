package net.lumalyte.lg.infrastructure.adapters.bukkit

import net.lumalyte.lg.domain.values.PlayerContext
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

/**
 * Adapter for converting between Bukkit Player and domain PlayerContext
 */
object BukkitPlayerAdapter {

    /**
     * Converts a Bukkit Player to a domain PlayerContext
     */
    fun Player.toPlayerContext(): PlayerContext {
        return PlayerContext(
            uuid = uniqueId,
            name = name,
            locale = locale().toString(),
            isOnline = isOnline,
            permissions = getAllPermissions()
        )
    }

    /**
     * Creates a PlayerContext from a UUID by looking up the player
     */
    fun fromUUID(uuid: UUID): PlayerContext? {
        val player = Bukkit.getPlayer(uuid)
        return if (player != null) {
            player.toPlayerContext()
        } else {
            // Try offline player
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline) {
                PlayerContext(
                    uuid = uuid,
                    name = offlinePlayer.name ?: "Unknown",
                    isOnline = false
                )
            } else {
                null
            }
        }
    }

    /**
     * Gets a Bukkit Player from a PlayerContext UUID
     */
    fun PlayerContext.toBukkitPlayer(): Player? {
        return Bukkit.getPlayer(uuid)
    }

    /**
     * Gets all effective permissions for a player
     */
    private fun Player.getAllPermissions(): Set<String> {
        return effectivePermissions
            .filter { it.value } // Only granted permissions
            .map { it.permission }
            .toSet()
    }

    /**
     * Checks if a PlayerContext corresponds to an online player
     */
    fun PlayerContext.isBukkitPlayerOnline(): Boolean {
        return Bukkit.getPlayer(uuid)?.isOnline == true
    }

    /**
     * Gets the Bukkit player if online, otherwise null
     */
    fun PlayerContext.getOnlinePlayer(): Player? {
        return if (isOnline) Bukkit.getPlayer(uuid) else null
    }
}

// Extension functions for easier usage
fun Player.toPlayerContext(): PlayerContext = BukkitPlayerAdapter.run { toPlayerContext() }
fun PlayerContext.toBukkitPlayer(): Player? = BukkitPlayerAdapter.run { toBukkitPlayer() }
fun PlayerContext.getOnlinePlayer(): Player? = BukkitPlayerAdapter.getOnlinePlayer()
fun UUID.toPlayerContext(): PlayerContext? = BukkitPlayerAdapter.fromUUID(this)
