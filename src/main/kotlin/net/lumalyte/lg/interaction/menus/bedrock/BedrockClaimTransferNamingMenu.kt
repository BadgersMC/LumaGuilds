package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import java.util.logging.Logger

/**
 * Bedrock Edition claim transfer naming menu using Cumulus CustomForm
 * Allows naming a claim during transfer acceptance
 */
class BedrockClaimTransferNamingMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claimId: java.util.UUID,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "claim.transfer.naming.title"))
            .label(bedrockLocalization.getBedrockString(player, "claim.transfer.naming.instructions"))
            .input(
                bedrockLocalization.getBedrockString(player, "claim.transfer.naming.name"),
                bedrockLocalization.getBedrockString(player, "claim.transfer.naming.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val newName = response.asInput(2)?.trim() ?: ""

                if (newName.length !in 1..50) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.transfer.naming.invalid"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.transfer.naming.success", newName))
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
