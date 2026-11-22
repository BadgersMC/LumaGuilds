package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualiserMode
import net.lumalyte.lg.application.actions.player.visualisation.ToggleVisualiserMode
import net.lumalyte.lg.application.results.player.visualisation.GetVisualiserModeResult
import net.lumalyte.lg.application.results.player.visualisation.ToggleVisualiserModeResult
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import java.util.logging.Logger

/**
 * Bedrock Edition edit tool menu using Cumulus SimpleForm
 * Provides information about claim editing tools
 */
class BedrockEditToolMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    logger: Logger,
    dependencies: BedrockMenuDependencies,
) : BaseBedrockMenu(menuNavigator, player, logger, dependencies) {

    private val lang: LangService by services.langService
    private val getVisualiserMode: GetVisualiserMode by services.getVisualiserMode
    private val toggleVisualiserMode: ToggleVisualiserMode by services.toggleVisualiserMode

    override fun getForm(): Form {
        val controller = BedrockEditToolController(
            getMode = { playerId ->
                when (val result = getVisualiserMode.execute(playerId)) {
                    is GetVisualiserModeResult.Success -> result.visualiserMode
                    GetVisualiserModeResult.StorageError -> 0
                }
            },
            toggleMode = { playerId ->
                when (val result = toggleVisualiserMode.execute(playerId)) {
                    is ToggleVisualiserModeResult.Success -> BedrockEditToolToggleResult.Changed(result.visualiserMode)
                    is ToggleVisualiserModeResult.OnCooldown -> BedrockEditToolToggleResult.OnCooldown(result.cooldownTime)
                }
            }
        )
        val mode = controller.currentMode(player.uniqueId)
        val content = if (mode == 0) {
            lang.bedrock("bedrock.edit_tool.mode.view")
        } else {
            lang.bedrock("bedrock.edit_tool.mode.edit")
        }

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.edit_tool.title"))
            .content(content)
            .button(lang.bedrock("bedrock.edit_tool.button.toggle"))
            .button(lang.bedrock("bedrock.edit_tool.button.close"))
            .validResultHandler { response ->
                if (response.clickedButtonId() == 0) {
                    when (val result = controller.toggle(player.uniqueId)) {
                        is BedrockEditToolToggleResult.Changed ->
                            player.sendMessage(lang.msg("bedrock.edit_tool.feedback.toggled"))
                        is BedrockEditToolToggleResult.OnCooldown ->
                            player.sendMessage(lang.msg("bedrock.edit_tool.feedback.cooldown", "seconds" to result.seconds))
                    }
                    open()
                } else {
                    bedrockNavigator.goBack()
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
