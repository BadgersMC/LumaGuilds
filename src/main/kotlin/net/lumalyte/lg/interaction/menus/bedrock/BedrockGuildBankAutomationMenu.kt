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
 * Bedrock Edition guild bank automation menu using Cumulus SimpleForm
 * Displays automation information (placeholder for future implementation)
 */
class BedrockGuildBankAutomationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val bankService: BankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "bank.automation.title"))
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.automation.info")}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "bank.automation.coming.soon")}")
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "bank.automation.menu.title")} - ${guild.name}")
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
