package net.lumalyte.lg.interaction.brigadier

import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Utility for permission checking in Brigadier commands.
 * Provides reusable permission guards that can be applied to command nodes.
 */
object PermissionGuard {

    /**
     * Creates a permission requirement function for Brigadier command nodes.
     * @param node The permission node to check
     * @return A function that checks if the command source has the required permission
     */
    fun requires(node: String): (CommandSourceStack) -> Boolean = { source ->
        // TODO: Use proper hasPermission method when available
        when (val sender = source.sender) {
            is org.bukkit.command.CommandSender -> sender.hasPermission(node)
            else -> false
        }
    }

    /**
     * Creates a permission requirement that allows operators to bypass.
     * @param node The permission node to check for non-operators
     * @return A function that checks permission or operator status
     */
    fun requiresOrOp(node: String): (CommandSourceStack) -> Boolean = { source ->
        when (val sender = source.sender) {
            is org.bukkit.command.CommandSender -> sender.hasPermission(node) || sender.isOp
            else -> false
        }
    }

    /**
     * Creates a permission requirement that only operators can satisfy.
     * @return A function that checks for operator status only
     */
    fun requiresOp(): (CommandSourceStack) -> Boolean = { source ->
        when (val sender = source.sender) {
            is org.bukkit.command.CommandSender -> sender.isOp
            else -> false
        }
    }

    /**
     * Creates a permission requirement for console-only commands.
     * @return A function that checks if the sender is the console
     */
    fun requiresConsole(): (CommandSourceStack) -> Boolean = { source ->
        source.sender !is org.bukkit.entity.Player
    }

    /**
     * Creates a permission requirement for player-only commands.
     * @return A function that checks if the sender is a player
     */
    fun requiresPlayer(): (CommandSourceStack) -> Boolean = { source ->
        source.sender is org.bukkit.entity.Player
    }

    /**
     * Combines multiple permission requirements with AND logic.
     * @param requirements The permission requirements to combine
     * @return A function that requires all permissions to be satisfied
     */
    fun requiresAll(vararg requirements: (CommandSourceStack) -> Boolean): (CommandSourceStack) -> Boolean = { source ->
        requirements.all { it(source) }
    }

    /**
     * Combines multiple permission requirements with OR logic.
     * @param requirements The permission requirements to combine
     * @return A function that requires at least one permission to be satisfied
     */
    fun requiresAny(vararg requirements: (CommandSourceStack) -> Boolean): (CommandSourceStack) -> Boolean = { source ->
        requirements.any { it(source) }
    }
}
