package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = lang.raw("bedrock.claim_wide_permissions.content")

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.claim_wide_permissions.title", "claim" to claim.name))
            .content(content)
            .button(lang.raw("bedrock.claim_wide_permissions.button.back"))
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
