package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import java.util.logging.Logger

/**
 * Bedrock Edition claim transfer menu using Cumulus CustomForm
 * Allows transferring claim ownership to another player
 */
class BedrockClaimTransferMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.transfer.title")} - ${claim.name}")
            .label(bedrockLocalization.getBedrockString(player, "claim.transfer.warning"))
            .input(
                bedrockLocalization.getBedrockString(player, "claim.transfer.player.name"),
                bedrockLocalization.getBedrockString(player, "claim.transfer.player.placeholder"),
                ""
            )
            .toggle(
                bedrockLocalization.getBedrockString(player, "claim.transfer.confirm"),
                false
            )
            .validResultHandler { response ->
                val targetPlayerName = response.asInput(2)?.trim() ?: ""
                val confirmed = response.asToggle(3)

                if (!confirmed) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.transfer.not.confirmed"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                if (targetPlayerName.isEmpty()) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.transfer.name.required"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName)
                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.transfer.player.not.found", targetPlayerName))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                // Add transfer request
                claim.transferRequests[targetPlayer.uniqueId] = (System.currentTimeMillis() / 1000).toInt() + 300 // 5 minutes
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.transfer.request.sent", targetPlayerName))

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
