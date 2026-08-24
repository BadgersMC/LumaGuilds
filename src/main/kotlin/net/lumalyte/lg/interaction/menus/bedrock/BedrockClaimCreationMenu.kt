package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.claim_creation.title"))
            .label(lang.bedrock("bedrock.claim_creation.instructions"))
            .input(
                lang.bedrock("bedrock.claim_creation.name.label"),
                lang.bedrock("bedrock.claim_creation.name.placeholder"),
                ""
            )
            .input(
                lang.bedrock("bedrock.claim_creation.description.label"),
                lang.bedrock("bedrock.claim_creation.description.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val claimName = response.asInput(2)?.trim() ?: ""
                val claimDescription = response.asInput(3)?.trim() ?: ""

                if (claimName.length !in 1..50) {
                    player.sendMessage(lang.msg("bedrock.claim_creation.feedback.invalid_name"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                if (claimDescription.length > 300) {
                    player.sendMessage(lang.msg("bedrock.claim_creation.feedback.description_too_long"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                player.sendMessage(lang.msg("bedrock.claim_creation.feedback.info", "claim" to claimName))
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
