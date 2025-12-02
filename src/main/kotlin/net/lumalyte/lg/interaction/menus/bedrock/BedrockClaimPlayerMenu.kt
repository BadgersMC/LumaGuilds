package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim player menu using Cumulus CustomForm
 * Allows selecting/entering a player name to manage permissions
 */
class BedrockClaimPlayerMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.player.title")} - ${claim.name}")
            .label(bedrockLocalization.getBedrockString(player, "claim.player.instructions"))
            .input(
                bedrockLocalization.getBedrockString(player, "claim.player.name"),
                bedrockLocalization.getBedrockString(player, "claim.player.name.placeholder"),
                ""
            )
            .validResultHandler { response ->
                val playerName = response.asInput(2)?.trim() ?: ""

                if (playerName.isEmpty()) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.player.name.required"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val targetPlayer = Bukkit.getOfflinePlayer(playerName)
                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.player.not.found", playerName))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                // Open permissions menu for this player
                menuNavigator.openMenu(menuFactory.createClaimPlayerPermissionsMenu(menuNavigator, player, claim, targetPlayer))
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
