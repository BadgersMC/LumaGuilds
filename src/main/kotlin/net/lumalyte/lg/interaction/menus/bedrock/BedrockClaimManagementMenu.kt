package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.claim.ConvertClaimToGuild
import net.lumalyte.lg.application.results.claim.ConvertClaimToGuildResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim management menu using Cumulus SimpleForm
 * Main hub for managing a claim
 */
class BedrockClaimManagementMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val convertClaimToGuild: ConvertClaimToGuild by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = if (claim.description.isNotEmpty()) {
            lang.bedrock(
                "bedrock.claim_management.content.with_description",
                "claim" to claim.name,
                "x" to claim.position.x,
                "y" to claim.position.y,
                "z" to claim.position.z,
                "icon" to claim.icon,
                "description" to claim.description
            )
        } else {
            lang.bedrock(
                "bedrock.claim_management.content.without_description",
                "claim" to claim.name,
                "x" to claim.position.x,
                "y" to claim.position.y,
                "z" to claim.position.z,
                "icon" to claim.icon
            )
        }

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.claim_management.title", "claim" to claim.name))
            .content(content)
            .button(lang.bedrock("bedrock.claim_management.button.icon"))
            .button(lang.bedrock("bedrock.claim_management.button.rename"))
            .button(lang.bedrock("bedrock.claim_management.button.permissions"))
            .button(lang.bedrock("bedrock.claim_management.button.flags"))
            .apply {
                if (claim.teamId == null) {
                    button(lang.bedrock("bedrock.claim_management.button.convert"))
                }
            }
            .button(lang.bedrock("bedrock.claim_management.button.transfer"))
            .button(lang.bedrock("bedrock.claim_management.button.back"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> menuNavigator.openMenu(menuFactory.createClaimIconMenu(player, menuNavigator, claim))
                    1 -> menuNavigator.openMenu(menuFactory.createClaimNamingMenu(menuNavigator, player, claim))
                    2 -> menuNavigator.openMenu(menuFactory.createClaimTrustMenu(menuNavigator, player, claim))
                    3 -> menuNavigator.openMenu(menuFactory.createClaimFlagMenu(menuNavigator, player, claim))
                    4 -> {
                        if (claim.teamId == null) {
                            handleConvertToGuild()
                        } else {
                            menuNavigator.openMenu(menuFactory.createClaimTransferMenu(menuNavigator, claim, player))
                        }
                    }
                    5 -> {
                        if (claim.teamId == null) {
                            menuNavigator.openMenu(menuFactory.createClaimTransferMenu(menuNavigator, claim, player))
                        } else {
                            bedrockNavigator.goBack()
                        }
                    }
                    else -> bedrockNavigator.goBack()
                }
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleConvertToGuild() {
        val result = convertClaimToGuild.execute(claim.id, player.uniqueId)
        when (result) {
            is ConvertClaimToGuildResult.Success -> {
                player.sendMessage(lang.msg("bedrock.claim_management.feedback.converted"))
                bedrockNavigator.goBack()
            }
            is ConvertClaimToGuildResult.AlreadyGuildOwned -> {
                player.sendMessage(lang.msg("bedrock.claim_management.feedback.already_guild_owned"))
            }
            is ConvertClaimToGuildResult.ClaimNotFound -> {
                player.sendMessage(lang.msg("bedrock.claim_management.feedback.not_found"))
            }
            is ConvertClaimToGuildResult.NotClaimOwner -> {
                player.sendMessage(lang.msg("bedrock.claim_management.feedback.not_owner"))
            }
            is ConvertClaimToGuildResult.PlayerNotInGuild -> {
                player.sendMessage(lang.msg("bedrock.claim_management.feedback.not_in_guild"))
            }
            is ConvertClaimToGuildResult.StorageError -> {
                player.sendMessage(lang.msg("bedrock.claim_management.feedback.storage_error"))
            }
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
