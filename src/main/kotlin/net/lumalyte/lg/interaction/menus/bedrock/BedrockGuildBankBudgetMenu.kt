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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val currentBalance = bankService.getBalance(guild.id)

        val content = lang.legacy("bedrock.bank_budget.content", "balance" to currentBalance)

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.bank_budget.title", "guild" to guild.name))
            .content(content)
            .button(lang.raw("bedrock.bank_budget.button.back"))
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
