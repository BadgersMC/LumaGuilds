package net.lumalyte.lg.interaction.commands.admin

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Admin command to manually credit a player's Vault balance.
 * Used for resolving failed bank withdrawal transactions.
 *
 * Usage: /bankcredit <player> <amount>
 */
class BankCreditCommand(
    private val bankService: BankService
) : CommandExecutor, TabCompleter, KoinComponent {

    private val lang: LangService by inject()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("lumaguilds.admin.bank.credit")) {
            sender.sendMessage(lang.msg("admin.common.no_permission"))
            return true
        }

        if (args.size < 2) {
            sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.usage_bankcredit_player_amount"))
            return true
        }

        val targetName = args[0]
        val amountStr = args[1]

        // Parse amount
        val amount = amountStr.toIntOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.invalid_amount_must_be_a_positive_integer", "amount_str" to amountStr))
            return true
        }

        // Get target player UUID (online or offline)
        val targetPlayer = Bukkit.getPlayer(targetName)
        if (targetPlayer == null) {
            sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.player_not_found_or_not_online", "target_name" to targetName))
            sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.note_player_must_be_online_to_receive"))
            return true
        }

        // Credit the player
        sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.crediting_coins_to", "amount" to amount, "player" to targetPlayer.name))

        val success = bankService.depositPlayer(
            targetPlayer.uniqueId,
            amount,
            "Admin credit by ${sender.name}"
        )

        if (success) {
            sender.sendMessage(lang.msg("admin.bank_credit.success", "amount" to amount, "player" to targetPlayer.name))
            sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.new_balance_coins", "unique_id" to bankService.getPlayerBalance(targetPlayer.uniqueId)))

            // Notify the player
            targetPlayer.sendMessage(lang.msg("admin.migrated.bank_credit.command.you_have_been_credited_coins_by_an", "amount" to amount))
        } else {
            sender.sendMessage(lang.msg("admin.bank_credit.failure"))
            sender.sendMessage(lang.msg("admin.migrated.bank_credit.command.check_server_logs_for_details_vault_economy"))
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
