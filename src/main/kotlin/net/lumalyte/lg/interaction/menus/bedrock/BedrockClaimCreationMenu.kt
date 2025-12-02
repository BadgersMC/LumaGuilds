package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import java.util.logging.Logger

/**
 * Bedrock Edition claim creation menu using Cumulus CustomForm
 * Allows naming a new claim during creation
 */
class BedrockClaimCreationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "claim.creation.title"))
            .label(bedrockLocalization.getBedrockString(player, "claim.creation.instructions"))
            .input(
                bedrockLocalization.getBedrockString(player, "claim.creation.name"),
                bedrockLocalization.getBedrockString(player, "claim.creation.placeholder"),
                ""
            )
            .input(
                bedrockLocalization.getBedrockString(player, "claim.creation.description"),
                bedrockLocalization.getBedrockString(player, "claim.creation.description.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val claimName = response.asInput(2)?.trim() ?: ""
                val claimDescription = response.asInput(3)?.trim() ?: ""

                if (claimName.length !in 1..50) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.creation.name.invalid"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                if (claimDescription.length > 300) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.creation.description.too.long"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.creation.info", claimName))
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
