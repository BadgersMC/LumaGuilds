package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val flags = getClaimFlags.execute(claim.id)

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "claim.flags.title"))
            appendLine()
            if (flags.isEmpty()) {
                appendLine(bedrockLocalization.getBedrockString(player, "claim.flags.none"))
            } else {
                appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.flags.total", flags.size)}")
                appendLine()
                flags.take(10).forEach { flag ->
                    appendLine("§7• $flag")
                }
            }
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.flags.menu.title")} - ${claim.name}")
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
