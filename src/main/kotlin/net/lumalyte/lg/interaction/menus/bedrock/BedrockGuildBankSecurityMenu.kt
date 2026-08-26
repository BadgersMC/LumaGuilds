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
 * Bedrock Edition guild bank security menu using Cumulus SimpleForm
 * Edits the persisted guild-bank dual-authorization threshold.
 */
class BedrockGuildBankSecurityMenu(
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
            .title(lang.bedrock("bedrock.bank_security.title", "guild" to guild.name))
            .input(
                lang.bedrock("bedrock.bank_security.dual_auth_threshold"),
                "0",
                settings.dualAuthThreshold.toString()
            )
            .validResultHandler { response ->
                val saved = editor.saveSecurity(guild.id, response.asInput(0) ?: "")
                if (saved) {
                    player.sendMessage(lang.msg("menu.bank_security.feedback.saved"))
                    bedrockNavigator.goBack()
                } else {
                    player.sendMessage(lang.msg("bedrock.bank_security.invalid"))
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
