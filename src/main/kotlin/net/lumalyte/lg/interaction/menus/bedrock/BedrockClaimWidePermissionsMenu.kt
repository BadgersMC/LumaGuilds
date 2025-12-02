package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import java.util.logging.Logger

/**
 * Bedrock Edition claim wide permissions menu using Cumulus SimpleForm
 * Manages default/public permissions for the claim
 */
class BedrockClaimWidePermissionsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "claim.wide.permissions.title"))
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.wide.permissions.info")}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.wide.permissions.coming.soon")}")
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.wide.permissions.menu.title")} - ${claim.name}")
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
