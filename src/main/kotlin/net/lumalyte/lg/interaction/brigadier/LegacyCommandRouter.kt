package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.MessageService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Routes unmigrated Brigadier commands to legacy command handlers.
 * Provides fallback execution for commands that haven't been fully migrated to Brigadier yet.
 */
object LegacyCommandRouter : KoinComponent {

    private val messageService: MessageService by inject()

    /**
     * Routes a command to the legacy command system.
     * This is called when a Brigadier command executor determines the command isn't implemented yet.
     *
     * @param context The Brigadier command context
     * @param legacyCommand The name of the legacy command to route to
     * @param args The arguments to pass to the legacy command
     * @return Command result (0 for success)
     */
    fun routeToLegacy(context: CommandContext<CommandSourceStack>, legacyCommand: String, args: Array<String> = emptyArray()): Int {
        val sender = context.source.sender

        // For now, just show that routing would happen - actual implementation needs legacy command system
        if (sender is org.bukkit.entity.Player) {
            messageService.renderSystem("command.routing_to_legacy", mapOf(
                "command" to legacyCommand,
                "args" to args.joinToString(" ")
            )).let { sender.sendMessage(it) }
        }

        // TODO: Actually route to legacy command system
        // This would involve:
        // 1. Getting the legacy command manager (ACF)
        // 2. Finding the appropriate legacy command handler
        // 3. Executing it with the provided context and arguments

        return 0
    }

    /**
     * Checks if a Brigadier command has been fully implemented.
     * This can be used by executors to decide whether to handle the command or route to legacy.
     *
     * @param commandName The name of the command to check
     * @return true if the command is implemented in Brigadier, false if it should route to legacy
     */
    fun isCommandImplemented(commandName: String): Boolean {
        // TODO: Implement command implementation tracking
        // This could be a registry of implemented commands, or check if executor does more than show "not implemented"

        // For now, return false for all commands (route everything to legacy)
        // In practice, this would check a registry of implemented commands
        return false
    }

    /**
     * Gets the appropriate legacy command routing for a given Brigadier command.
     * Maps Brigadier command structures back to legacy command names and argument formats.
     *
     * @param brigadierCommand The Brigadier command name
     * @param subcommand The subcommand (if any)
     * @return Pair of (legacyCommandName, shouldRoute)
     */
    fun getLegacyRouting(brigadierCommand: String, subcommand: String? = null): Pair<String, Boolean> {
        return when (brigadierCommand) {
            "claim" -> {
                when (subcommand) {
                    "trust" -> "claim trust" to true
                    "untrust" -> "claim untrust" to true
                    "trustall" -> "claim trustall" to true
                    "untrustall" -> "claim untrustall" to true
                    "trustlist" -> "claim trustlist" to true
                    "addflag" -> "claim addflag" to true
                    "removeflag" -> "claim removeflag" to true
                    "description" -> "claim description" to true
                    "info" -> "claim info" to true
                    "rename" -> "claim rename" to true
                    "remove" -> "claim remove" to true
                    "partitions" -> "claim partitions" to true
                    else -> "claim" to true
                }
            }
            "claimlist" -> "claimlist" to true
            "claimmenu" -> "claimmenu" to true
            "claimoverride" -> "claimoverride" to true
            "guild" -> "guild" to true
            "pc", "pchat", "partychat" -> "pc" to true
            "lumaguilds", "bellclaims" -> "lumaguilds" to true
            "bedrockcachestats" -> "bedrockcachestats" to true
            "adminsurveillance" -> "adminsurveillance" to true
            else -> brigadierCommand to false
        }
    }
}
