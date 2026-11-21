package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import java.util.logging.Logger

/**
 * Bedrock Edition guild bank statistics menu using Cumulus SimpleForm
 * Displays financial insights and trends
 */
class BedrockGuildBankStatisticsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger,
    dependencies: BedrockMenuDependencies,
) : BaseBedrockMenu(menuNavigator, player, logger, dependencies) {

    private val bankService: BankService by services.bankService
    private val lang: LangService by services.langService

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get bank statistics
        val bankStats = bankService.getBankStats(guild.id)
        val currentBalance = bankService.getBalance(guild.id)

        val content = lang.bedrock(
            "bedrock.bank.statistics.content",
            "balance" to currentBalance,
            "deposits" to bankStats.totalDeposits,
            "withdrawals" to bankStats.totalWithdrawals,
            "net" to bankStats.totalDeposits - bankStats.totalWithdrawals,
            "transactions" to bankStats.totalTransactions,
            "volume" to bankStats.transactionVolume
        )

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.bank.statistics.title", "guild" to guild.name))
            .content(content)
            .button(lang.bedrock("bedrock.bank.statistics.button.refresh"))
            .button(lang.bedrock("bedrock.bank.statistics.button.back"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> {
                        // Refresh - reopen the menu
                        bedrockNavigator.openMenu(BedrockGuildBankStatisticsMenu(menuNavigator, player, guild, logger, dependencies = bedrockDependencies))
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
