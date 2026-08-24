package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get recent transactions
        val transactions = bankService.getTransactionHistory(guild.id, 15) // Last 15 transactions
        val dateFormatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.bank.history.date_format"))
            .withZone(ZoneId.systemDefault())

        val content = if (transactions.isEmpty()) {
            lang.legacy("bedrock.bank.history.empty")
        } else {
            val rows = transactions.joinToString("\n\n") { transaction ->
                val playerName = Bukkit.getOfflinePlayer(transaction.actorId).name
                    ?: lang.raw("bedrock.bank.history.unknown_player")
                when {
                    transaction.amount > 0 -> lang.legacy(
                        "bedrock.bank.history.row.deposit",
                        "timestamp" to dateFormatter.format(transaction.timestamp),
                        "player" to playerName,
                        "amount" to transaction.amount,
                        "description" to transaction.description
                    )
                    transaction.amount < 0 -> lang.legacy(
                        "bedrock.bank.history.row.withdrawal",
                        "timestamp" to dateFormatter.format(transaction.timestamp),
                        "player" to playerName,
                        "amount" to transaction.amount,
                        "description" to transaction.description
                    )
                    else -> lang.legacy(
                        "bedrock.bank.history.row.neutral",
                        "timestamp" to dateFormatter.format(transaction.timestamp),
                        "player" to playerName,
                        "amount" to transaction.amount,
                        "description" to transaction.description
                    )
                }
            }
            lang.legacy("bedrock.bank.history.content", "transactions" to rows)
        }

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.bank.history.title", "guild" to guild.name))
            .content(content)
            .button(lang.raw("bedrock.bank.history.button.refresh"))
            .button(lang.raw("bedrock.bank.history.button.back"))
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
