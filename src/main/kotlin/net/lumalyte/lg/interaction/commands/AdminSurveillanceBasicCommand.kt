package net.lumalyte.lg.interaction.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Basic implementation of the /adminsurveillance command using traditional Bukkit system.
 * This is a temporary solution during the ACF to Brigadier migration.
 */
class AdminSurveillanceBasicCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lumaguilds.admin")) {
            sender.sendMessage("§cYou don't have permission to use admin surveillance!")
            return true
        }

        when {
            args.isEmpty() -> {
                sender.sendMessage("§6Admin Surveillance §8- §7Guild monitoring system")
                sender.sendMessage("§7Use §e/adminsurveillance help §7for available options")
                return true
            }
            args[0].equals("help", ignoreCase = true) -> {
                showHelp(sender)
                return true
            }
            args[0].equals("status", ignoreCase = true) -> {
                sender.sendMessage("§6Admin Surveillance Status:")
                sender.sendMessage("§7System: §eBasic Implementation")
                sender.sendMessage("§7Migration: §eIn Progress")
                return true
            }
            else -> {
                sender.sendMessage("§cUnknown option: ${args[0]}")
                sender.sendMessage("§7Use §e/adminsurveillance help §7for available options")
                return true
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Admin Surveillance Help ===")
        sender.sendMessage("§e/adminsurveillance help §7- Show this help message")
        sender.sendMessage("§e/adminsurveillance status §7- Show system status")
        sender.sendMessage("§6===============================")
    }
}
