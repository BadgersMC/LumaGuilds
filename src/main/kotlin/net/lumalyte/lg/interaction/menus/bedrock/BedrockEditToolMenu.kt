package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = lang.bedrock("bedrock.edit_tool.content")

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.edit_tool.title"))
            .content(content)
            .button(lang.bedrock("bedrock.edit_tool.button.close"))
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
