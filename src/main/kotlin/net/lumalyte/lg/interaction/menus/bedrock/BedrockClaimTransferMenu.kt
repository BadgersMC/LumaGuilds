package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.claim.transfer.OfferPlayerTransferRequest
import net.lumalyte.lg.application.results.claim.transfer.OfferPlayerTransferRequestResult
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
 * Bedrock Edition claim transfer menu using Cumulus CustomForm
 * Allows transferring claim ownership to another player
 */
class BedrockClaimTransferMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val lang: LangService by inject()
    private val offerPlayerTransferRequest: OfferPlayerTransferRequest by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.claim_transfer.title", "claim" to claim.name))
            .label(lang.bedrock("bedrock.claim_transfer.warning"))
            .input(
                lang.bedrock("bedrock.claim_transfer.player.label"),
                lang.bedrock("bedrock.claim_transfer.player.placeholder"),
                ""
            )
            .toggle(
                lang.bedrock("bedrock.claim_transfer.confirm"),
                false
            )
            .validResultHandler { response ->
                val targetPlayerName = response.asInput(2)?.trim() ?: ""
                val confirmed = response.asToggle(3)

                if (!confirmed) {
                    player.sendMessage(lang.msg("bedrock.claim_transfer.feedback.not_confirmed"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                if (targetPlayerName.isEmpty()) {
                    player.sendMessage(lang.msg("bedrock.claim_transfer.feedback.name_required"))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                val targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName)
                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    player.sendMessage(lang.msg("bedrock.claim_transfer.feedback.player_not_found", "player" to targetPlayerName))
                    bedrockNavigator.goBack()
                    return@validResultHandler
                }

                when (offerPlayerTransferRequest.execute(claim.id, targetPlayer.uniqueId)) {
                    OfferPlayerTransferRequestResult.Success ->
                        player.sendMessage(
                            lang.msg(
                                "bedrock.claim_transfer.feedback.sent",
                                "player" to targetPlayerName,
                            )
                        )
                    OfferPlayerTransferRequestResult.RequestAlreadyPending ->
                        player.sendMessage(
                            lang.msg(
                                "bedrock.claim_transfer.feedback.already_pending",
                                "player" to targetPlayerName,
                            )
                        )
                    OfferPlayerTransferRequestResult.ClaimNotFound,
                    OfferPlayerTransferRequestResult.StorageError ->
                        player.sendMessage(lang.msg("bedrock.claim_transfer.feedback.storage_error"))
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
