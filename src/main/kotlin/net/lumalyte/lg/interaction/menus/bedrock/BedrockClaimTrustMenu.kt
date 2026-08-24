package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val trustedPlayers = getPlayersWithPermissionInClaim.execute(claim.id)

        val content = if (trustedPlayers.isEmpty()) {
            lang.bedrock("bedrock.claim_trust.content.empty")
        } else {
            val rows = trustedPlayers.take(10).joinToString("\n") { playerId ->
                val playerName = Bukkit.getOfflinePlayer(playerId).name ?: lang.bedrock("menu.common.unknown_player")
                lang.bedrock("bedrock.claim_trust.player_row", "player" to playerName)
            }
            val overflow = if (trustedPlayers.size > 10) {
                lang.bedrock("bedrock.claim_trust.more", "count" to trustedPlayers.size - 10)
            } else {
                lang.bedrock("menu.common.blank")
            }
            lang.bedrock(
                "bedrock.claim_trust.content.list",
                "count" to trustedPlayers.size,
                "players" to rows,
                "overflow" to overflow
            )
        }

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.claim_trust.title", "claim" to claim.name))
            .content(content)
            .button(lang.bedrock("bedrock.claim_trust.button.add"))
            .button(lang.bedrock("bedrock.claim_trust.button.remove"))
            .button(lang.bedrock("bedrock.claim_trust.button.wide_permissions"))
            .button(lang.bedrock("bedrock.claim_trust.button.back"))
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
                            player.sendMessage(lang.msg("bedrock.claim_trust.feedback.none_to_remove"))
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
