package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        // Get bank statistics
        val bankStats = bankService.getBankStats(guild.id)
        val currentBalance = bankService.getBalance(guild.id)

        val content = lang.legacy(
            "bedrock.bank.statistics.content",
            "balance" to currentBalance,
            "deposits" to bankStats.totalDeposits,
            "withdrawals" to bankStats.totalWithdrawals,
            "net" to bankStats.totalDeposits - bankStats.totalWithdrawals,
            "transactions" to bankStats.totalTransactions,
            "volume" to bankStats.transactionVolume
        )

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.bank.statistics.title", "guild" to guild.name))
            .content(content)
            .button(lang.raw("bedrock.bank.statistics.button.refresh"))
            .button(lang.raw("bedrock.bank.statistics.button.back"))
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
