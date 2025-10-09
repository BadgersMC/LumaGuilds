package net.lumalyte.lg.interaction.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Basic implementation of the /bedrockcachestats command using traditional Bukkit system.
 * This is a temporary solution during the ACF to Brigadier migration.
 */
class BedrockCacheStatsBasicCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lumaguilds.admin")) {
            sender.sendMessage("§cYou don't have permission to view bedrock cache stats!")
            return true
        }

        sender.sendMessage("§6=== Bedrock Cache Statistics ===")
        sender.sendMessage("§7Cache System: §eBasic Implementation")
        sender.sendMessage("§7Migration: §eIn Progress")
        sender.sendMessage("§7Status: §aOperational")
        sender.sendMessage("§6===============================")
        
        return true
    }
}
