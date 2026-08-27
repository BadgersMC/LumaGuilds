package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.EmojiPermissionGateway
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class LuckPermsEmojiPermissionGateway(
    private val plugin: Plugin,
) : EmojiPermissionGateway {
    private val logger = LoggerFactory.getLogger(LuckPermsEmojiPermissionGateway::class.java)

    override fun grant(playerId: UUID, permission: String): Boolean =
        dispatch(playerId, permission, "set $permission true")

    override fun revoke(playerId: UUID, permission: String): Boolean =
        dispatch(playerId, permission, "unset $permission")

    private fun dispatch(playerId: UUID, permission: String, operation: String): Boolean {
        if (!PERMISSION_NODE.matches(permission)) {
            logger.warn("Rejected invalid managed emoji permission for player $playerId")
            return false
        }
        val command = "lp user $playerId permission $operation"
        return try {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            } else {
                Bukkit.getScheduler()
                    .callSyncMethod(plugin, Callable { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command) })
                    .get(5, TimeUnit.SECONDS)
            }
        } catch (exception: Exception) {
            logger.error("Failed to dispatch managed emoji permission operation for player $playerId", exception)
            false
        }
    }

    companion object {
        private val PERMISSION_NODE = Regex("^[a-z0-9][a-z0-9_.-]*$")
    }
}
