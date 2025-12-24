package net.lumalyte.lg.interaction.commands.admin

import net.lumalyte.lg.application.services.BankService
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Admin command to manually credit a player's Vault balance.
 * Used for resolving failed bank withdrawal transactions.
 *
 * Usage: /bankcredit <player> <amount>
 */
class BankCreditCommand(
    private val bankService: BankService
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("lumaguilds.admin.bank.credit")) {
            sender.sendMessage("§cYou don't have permission to use this command")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /bankcredit <player> <amount>")
            return true
        }

        val targetName = args[0]
        val amountStr = args[1]

        // Parse amount
        val amount = amountStr.toIntOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage("§cInvalid amount: $amountStr (must be a positive integer)")
            return true
        }

        // Get target player UUID (online or offline)
        val targetPlayer = Bukkit.getPlayer(targetName)
        if (targetPlayer == null) {
            sender.sendMessage("§cPlayer '$targetName' not found or not online")
            sender.sendMessage("§7Note: Player must be online to receive credits")
            return true
        }

        // Credit the player
        sender.sendMessage("§eCrediting §f$amount §ecoins to §f${targetPlayer.name}§e...")

        val success = bankService.depositPlayer(
            targetPlayer.uniqueId,
            amount,
            "Admin credit by ${sender.name}"
        )

        if (success) {
            sender.sendMessage("§a✓ Successfully credited §f$amount §acoins to §f${targetPlayer.name}")
            sender.sendMessage("§7New balance: §f${bankService.getPlayerBalance(targetPlayer.uniqueId)} coins")

            // Notify the player
            targetPlayer.sendMessage("§a✓ You have been credited §f$amount §acoins by an administrator")
        } else {
            sender.sendMessage("§c✗ Failed to credit player")
            sender.sendMessage("§7Check server logs for details (Vault economy may be unavailable)")
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("lumaguilds.admin.bank.credit")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                // Online player names
                Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                // Common amounts
                listOf("100", "500", "1000", "5000", "10000")
                    .filter { it.startsWith(args[1]) }
            }
            else -> emptyList()
        }
    }
}
