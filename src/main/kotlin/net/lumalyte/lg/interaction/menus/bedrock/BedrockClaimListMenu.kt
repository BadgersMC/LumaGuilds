package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim list menu using Cumulus SimpleForm
 * Shows all claims owned by the player
 */
class BedrockClaimListMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val listPlayerClaims: ListPlayerClaims by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val claims = listPlayerClaims.execute(player.uniqueId)

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "claim.list.title"))
            appendLine()
            if (claims.isEmpty()) {
                appendLine(bedrockLocalization.getBedrockString(player, "claim.list.none"))
            } else {
                appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.list.total", claims.size)}")
            }
        }

        return SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "claim.list.menu.title"))
            .content(content)
            .apply {
                if (claims.isNotEmpty()) {
                    claims.take(8).forEach { claim ->
                        val location = "${claim.position.x}, ${claim.position.y}, ${claim.position.z}"
                        button("§6${claim.name}§r\n§7$location")
                    }
                    if (claims.size > 8) {
                        button(bedrockLocalization.getBedrockString(player, "claim.list.more", claims.size - 8))
                    }
                }
                button(bedrockLocalization.getBedrockString(player, "common.close"))
            }
            .validResultHandler { response ->
                val buttonId = response.clickedButtonId()
                if (buttonId < claims.size.coerceAtMost(8)) {
                    val selectedClaim = claims[buttonId]
                    menuNavigator.openMenu(menuFactory.createClaimManagementMenu(menuNavigator, player, selectedClaim))
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
