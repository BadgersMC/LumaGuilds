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
 * Bedrock Edition guild bank budget menu using Cumulus SimpleForm
 * Displays budget information (placeholder for future implementation)
 */
class BedrockGuildBankBudgetMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankService: BankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val currentBalance = bankService.getBalance(guild.id)

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "bank.budget.title"))
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.balance")}: §6$currentBalance")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.budget.info")}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.budget.coming.soon")}")
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "bank.budget.menu.title")} - ${guild.name}")
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "common.back"))
            .validResultHandler { _ ->
                bedrockNavigator.goBack()
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
