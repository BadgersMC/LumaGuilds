package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim flag menu using Cumulus SimpleForm
 * Shows and manages claim flags/settings
 */
class BedrockClaimFlagMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val getClaimFlags: GetClaimFlags by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val flags = getClaimFlags.execute(claim.id)

        val content = if (flags.isEmpty()) {
            lang.raw("bedrock.claim_flags.content.empty")
        } else {
            val rows = flags.take(10).joinToString("\n") { flag ->
                lang.legacy("bedrock.claim_flags.row", "flag" to flag)
            }
            lang.legacy("bedrock.claim_flags.content.list", "count" to flags.size, "flags" to rows)
        }

        return SimpleForm.builder()
            .title(lang.legacy("bedrock.claim_flags.title", "claim" to claim.name))
            .content(content)
            .button(lang.raw("bedrock.claim_flags.button.back"))
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
