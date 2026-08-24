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
 * Bedrock Edition claim transfer naming menu using Cumulus CustomForm
 * Allows naming a claim during transfer acceptance
 */
class BedrockClaimTransferNamingMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claimId: java.util.UUID,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.claim_transfer_naming.title"))
            .label(lang.bedrock("bedrock.claim_transfer_naming.instructions"))
            .input(
                lang.bedrock("bedrock.claim_transfer_naming.name.label"),
                lang.bedrock("bedrock.claim_transfer_naming.name.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val newName = response.asInput(2)?.trim() ?: ""

                if (newName.length !in 1..50) {
                    player.sendMessage(lang.msg("bedrock.claim_transfer_naming.feedback.invalid"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                player.sendMessage(lang.msg("bedrock.claim_transfer_naming.feedback.success", "claim" to newName))
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
