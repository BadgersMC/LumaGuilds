package net.lumalyte.lg.domain.values

import java.util.*

/**
 * Platform-agnostic representation of a player.
 * This abstraction allows the domain layer to work with player information
 * without depending on any specific game engine (Bukkit, Hytale, etc.)
 */
data class PlayerContext(
    val uuid: UUID,
    val name: String,
    val locale: String = "en_US",
    val isOnline: Boolean = true,
    val permissions: Set<String> = emptySet()
) {
    init {
        require(name.isNotBlank()) { "Player name cannot be blank" }
    }

    /**
     * Checks if this player has a specific permission
     */
    fun hasPermission(permission: String): Boolean {
        return permissions.contains(permission) ||
               permissions.contains("*") ||
               hasWildcardPermission(permission)
    }

    /**
     * Checks for wildcard permissions
     * Example: if player has "lumaguilds.admin.*", they have "lumaguilds.admin.delete"
     */
    private fun hasWildcardPermission(permission: String): Boolean {
        val parts = permission.split(".")
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (permissions.contains(wildcard)) {
                return true
            }
        }
        return false
    }

    /**
     * Creates a copy of this player context with updated online status
     */
    fun withOnlineStatus(online: Boolean): PlayerContext = copy(isOnline = online)

    /**
     * Creates a copy of this player context with additional permissions
     */
    fun withPermissions(vararg perms: String): PlayerContext {
        return copy(permissions = permissions + perms)
    }

    companion object {
        /**
         * Creates a minimal player context with just UUID and name
         */
        fun of(uuid: UUID, name: String): PlayerContext {
            return PlayerContext(uuid = uuid, name = name)
        }

        /**
         * Creates an offline player context
         */
        fun offline(uuid: UUID, name: String): PlayerContext {
            return PlayerContext(uuid = uuid, name = name, isOnline = false)
        }
    }
}
