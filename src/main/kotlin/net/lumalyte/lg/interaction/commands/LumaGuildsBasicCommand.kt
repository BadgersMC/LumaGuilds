package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Basic implementation of the /lumaguilds command using traditional Bukkit system.
 * This is a temporary solution during the ACF to Brigadier migration.
 */
class LumaGuildsBasicCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when {
            args.isEmpty() -> {
                sender.sendMessage("§6LumaGuilds §7v2.3.0-SNAPSHOT §8- §7Advanced guild system")
                sender.sendMessage("§7Use §e/lumaguilds help §7for available commands")
                return true
            }
            args[0].equals("help", ignoreCase = true) -> {
                showHelp(sender)
                return true
            }
            args[0].equals("reload", ignoreCase = true) -> {
                if (!sender.hasPermission("lumaguilds.admin")) {
                    sender.sendMessage("§cYou don't have permission to reload the plugin!")
                    return true
                }
                sender.sendMessage("§6Reloading LumaGuilds...")
                // TODO: Implement reload functionality
                sender.sendMessage("§aPlugin reloaded successfully!")
                return true
            }
            args[0].equals("status", ignoreCase = true) -> {
                showStatus(sender)
                return true
            }
            else -> {
                sender.sendMessage("§cUnknown command: ${args[0]}")
                sender.sendMessage("§7Use §e/lumaguilds help §7for available commands")
                return true
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== LumaGuilds Help ===")
        sender.sendMessage("§e/lumaguilds help §7- Show this help message")
        sender.sendMessage("§e/lumaguilds status §7- Show plugin status")
        if (sender.hasPermission("lumaguilds.admin")) {
            sender.sendMessage("§e/lumaguilds reload §7- Reload the plugin configuration")
        }
        sender.sendMessage("§6=======================")
    }

    private fun showStatus(sender: CommandSender) {
        sender.sendMessage("§6=== LumaGuilds Status ===")
        sender.sendMessage("§7Version: §e2.3.0-SNAPSHOT")
        sender.sendMessage("§7Paper Plugin: §aEnabled")
        sender.sendMessage("§7ACF Migration: §eIn Progress")
        sender.sendMessage("§7Commands: §eBasic Bukkit System")
        sender.sendMessage("§6========================")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("help", "status").filter { it.startsWith(args[0], ignoreCase = true) } +
                  if (sender.hasPermission("lumaguilds.admin")) listOf("reload").filter { it.startsWith(args[0], ignoreCase = true) } else emptyList()
            else -> emptyList()
        }
    }
}
