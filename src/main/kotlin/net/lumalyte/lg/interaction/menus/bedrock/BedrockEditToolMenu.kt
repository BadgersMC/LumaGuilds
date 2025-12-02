package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import java.util.logging.Logger

/**
 * Bedrock Edition edit tool menu using Cumulus SimpleForm
 * Provides information about claim editing tools
 */
class BedrockEditToolMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "claim.edit.tool.title"))
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.edit.tool.info")}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.edit.tool.instructions")}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.edit.tool.coming.soon")}")
        }

        return SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "claim.edit.tool.menu.title"))
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "common.close"))
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
