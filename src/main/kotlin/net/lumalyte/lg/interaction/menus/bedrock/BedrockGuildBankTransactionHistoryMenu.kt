package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild bank transaction history menu using Cumulus SimpleForm
 * Shows recent bank transactions
 */
class BedrockGuildBankTransactionHistoryMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankService: BankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get recent transactions
        val transactions = bankService.getTransactionHistory(guild.id, 15) // Last 15 transactions
        val dateFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "bank.history.title"))
            appendLine()

            if (transactions.isEmpty()) {
                appendLine(bedrockLocalization.getBedrockString(player, "bank.history.none"))
            } else {
                appendLine("§7=== ${bedrockLocalization.getBedrockString(player, "bank.history.recent")} ===")
                appendLine()
                transactions.forEach { transaction ->
                    val playerName = Bukkit.getOfflinePlayer(transaction.actorId).name ?: "Unknown"

                    val timestamp = dateFormatter.format(transaction.timestamp)
                    val amountColor = when {
                        transaction.amount > 0 -> "§a+"
                        transaction.amount < 0 -> "§c"
                        else -> "§7"
                    }

                    appendLine("§7[$timestamp] §f$playerName")
                    appendLine("  ${amountColor}${transaction.amount} §7- ${transaction.description}")
                    appendLine()
                }
            }
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "bank.history.menu.title")} - ${guild.name}")
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "bank.history.refresh"))
            .button(bedrockLocalization.getBedrockString(player, "common.back"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> {
                        // Refresh
                        bedrockNavigator.openMenu(BedrockGuildBankTransactionHistoryMenu(menuNavigator, player, guild, logger))
                    }
                    1 -> {
                        // Back
                        bedrockNavigator.goBack()
                    }
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
