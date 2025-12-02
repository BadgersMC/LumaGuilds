package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim trust menu using Cumulus SimpleForm
 * Shows players with permissions in the claim
 */
class BedrockClaimTrustMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val trustedPlayers = getPlayersWithPermissionInClaim.execute(claim.id)

        val content = buildString {
            appendLine(bedrockLocalization.getBedrockString(player, "claim.trust.title"))
            appendLine()
            if (trustedPlayers.isEmpty()) {
                appendLine(bedrockLocalization.getBedrockString(player, "claim.trust.none"))
            } else {
                appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.trust.total", trustedPlayers.size)}")
                appendLine()
                trustedPlayers.take(10).forEach { playerId ->
                    val playerName = Bukkit.getOfflinePlayer(playerId).name ?: "Unknown"
                    appendLine("§7• §f$playerName")
                }
                if (trustedPlayers.size > 10) {
                    appendLine("§7... ${bedrockLocalization.getBedrockString(player, "claim.trust.more", trustedPlayers.size - 10)}")
                }
            }
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.trust.menu.title")} - ${claim.name}")
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "claim.trust.add"))
            .button(bedrockLocalization.getBedrockString(player, "claim.trust.remove"))
            .button(bedrockLocalization.getBedrockString(player, "claim.trust.wide.permissions"))
            .button(bedrockLocalization.getBedrockString(player, "common.back"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> {
                        // Add player - open player menu
                        menuNavigator.openMenu(menuFactory.createClaimPlayerMenu(menuNavigator, player, claim))
                    }
                    1 -> {
                        // Remove player - show list to select
                        if (trustedPlayers.isNotEmpty()) {
                            menuNavigator.openMenu(menuFactory.createClaimPlayerMenu(menuNavigator, player, claim))
                        } else {
                            player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.trust.none.to.remove"))
                        }
                    }
                    2 -> {
                        // Wide permissions
                        menuNavigator.openMenu(menuFactory.createClaimWidePermissionsMenu(menuNavigator, player, claim))
                    }
                    3 -> bedrockNavigator.goBack()
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
