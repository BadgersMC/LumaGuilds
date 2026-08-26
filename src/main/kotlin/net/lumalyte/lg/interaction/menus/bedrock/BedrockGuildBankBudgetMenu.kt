package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild bank budget menu using Cumulus SimpleForm
 * Edits the persisted monthly, weekly, and daily guild-bank budgets.
 */
class BedrockGuildBankBudgetMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankSettingsRepository: BankSettingsRepository by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val settings = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        val editor = BedrockBankSettingsEditor(bankSettingsRepository)
        return CustomForm.builder()
            .title(lang.bedrock("bedrock.bank_budget.title", "guild" to guild.name))
            .input(lang.bedrock("bedrock.bank_budget.monthly"), "0", settings.monthlyBudget.toString())
            .input(lang.bedrock("bedrock.bank_budget.weekly"), "0", settings.weeklyBudget.toString())
            .input(lang.bedrock("bedrock.bank_budget.daily"), "0", settings.dailyBudget.toString())
            .validResultHandler { response ->
                val saved = editor.saveBudgets(
                    guild.id,
                    response.asInput(0) ?: "",
                    response.asInput(1) ?: "",
                    response.asInput(2) ?: ""
                )
                if (saved) {
                    player.sendMessage(lang.msg("menu.bank_budget.feedback.saved"))
                    bedrockNavigator.goBack()
                } else {
                    player.sendMessage(lang.msg("bedrock.bank_budget.invalid"))
                    open()
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
