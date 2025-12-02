package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import java.util.UUID
import java.util.logging.Logger

/**
 * Bedrock Edition claim player permissions menu using Cumulus SimpleForm
 * Shows and manages permissions for a specific player in a claim
 */
class BedrockClaimPlayerPermissionsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    private val targetPlayerId: UUID,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val targetPlayerName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"

        val content = buildString {
            appendLine("§6${bedrockLocalization.getBedrockString(player, "claim.player.permissions.managing", targetPlayerName)}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.player.permissions.info")}")
            appendLine()
            appendLine("§7${bedrockLocalization.getBedrockString(player, "claim.player.permissions.coming.soon")}")
        }

        return SimpleForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "claim.player.permissions.title")} - $targetPlayerName")
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
