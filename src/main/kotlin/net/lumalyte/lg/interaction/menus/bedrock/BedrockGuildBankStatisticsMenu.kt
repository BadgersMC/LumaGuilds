package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild bank statistics menu using Cumulus SimpleForm
 * Displays financial insights and trends
 */
class BedrockGuildBankStatisticsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankService: BankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get bank statistics
        val bankStats = bankService.getBankStats(guild.id)
        val currentBalance = bankService.getBalance(guild.id)

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "bank.stats.title"))
            appendLine()
            appendLine("§6${bedrockLocalization.getBedrockString(player, "bank.balance")}: §f${currentBalance}")
            appendLine()
            appendLine("§7=== ${bedrockLocalization.getBedrockString(player, "bank.stats.overview")} ===")
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.stats.deposits")}: §a+${bankStats.totalDeposits}")
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.stats.withdrawals")}: §c-${bankStats.totalWithdrawals}")
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.stats.net")}: §f${bankStats.totalDeposits - bankStats.totalWithdrawals}")
            appendLine()
            appendLine("§7=== ${bedrockLocalization.getBedrockString(player, "bank.stats.activity")} ===")
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.stats.transactions")}: §f${bankStats.totalTransactions}")
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.stats.volume")}: §f${bankStats.transactionVolume}")
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "bank.stats.menu.title")} - ${guild.name}")
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "bank.stats.refresh"))
            .button(bedrockLocalization.getBedrockString(player, "common.back"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> {
                        // Refresh - reopen the menu
                        bedrockNavigator.openMenu(BedrockGuildBankStatisticsMenu(menuNavigator, player, guild, logger))
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
