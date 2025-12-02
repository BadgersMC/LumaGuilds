package net.lumalyte.lg.interaction.menus.bedrock

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

    override fun getForm(): Form {
        val config = getBedrockConfig()

        val content = buildString {
            appendLine("§6${claim.name}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.location")}: ${claim.position.x}, ${claim.position.y}, ${claim.position.z}")
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.icon")}: ${claim.icon}")
            if (claim.description.isNotEmpty()) {
                appendLine()
                appendLine("§7${claim.description}")
            }
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.management.title")} - ${claim.name}")
            .content(content)
            .button(bedrockLocalization.getBedrockString(player, "claim.management.icon"))
            .button(bedrockLocalization.getBedrockString(player, "claim.management.rename"))
            .button(bedrockLocalization.getBedrockString(player, "claim.management.permissions"))
            .button(bedrockLocalization.getBedrockString(player, "claim.management.flags"))
            .apply {
                if (claim.teamId == null) {
                    button(bedrockLocalization.getBedrockString(player, "claim.management.convert"))
                }
            }
            .button(bedrockLocalization.getBedrockString(player, "claim.management.transfer"))
            .button(bedrockLocalization.getBedrockString(player, "common.back"))
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
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.convert.success"))
                bedrockNavigator.goBack()
            }
            is ConvertClaimToGuildResult.AlreadyGuildOwned -> {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.convert.already.guild.owned"))
            }
            is ConvertClaimToGuildResult.ClaimNotFound -> {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.convert.not.found"))
            }
            is ConvertClaimToGuildResult.NotClaimOwner -> {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.convert.not.owner"))
            }
            is ConvertClaimToGuildResult.PlayerNotInGuild -> {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.convert.not.in.guild"))
            }
            is ConvertClaimToGuildResult.StorageError -> {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "claim.convert.error"))
            }
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
