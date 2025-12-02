package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim naming menu using Cumulus CustomForm
 * Allows renaming a claim and updating its description
 */
class BedrockClaimNamingMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val claimRepository: ClaimRepository by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.naming.title")} - ${claim.name}")
            .label(bedrockLocalization.getBedrockString(player, "claim.naming.instructions"))
            .input(
                bedrockLocalization.getBedrockString(player, "claim.naming.name"),
                bedrockLocalization.getBedrockString(player, "claim.naming.name.placeholder"),
                claim.name
            )
            .input(
                bedrockLocalization.getBedrockString(player, "claim.naming.description"),
                bedrockLocalization.getBedrockString(player, "claim.naming.description.placeholder"),
                claim.description
            )
            .validResultHandler { response ->
                val newName = response.asInput(2)?.trim() ?: claim.name
                val newDescription = response.asInput(3)?.trim() ?: claim.description

                // Validate name length
                if (newName.length !in 1..50) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.naming.name.invalid"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                // Validate description length
                if (newDescription.length > 300) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.naming.description.too.long"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                if (newName != claim.name || newDescription != claim.description) {
                    claimRepository.update(claim.copy(name = newName, description = newDescription))
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.naming.updated"))
                }

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
