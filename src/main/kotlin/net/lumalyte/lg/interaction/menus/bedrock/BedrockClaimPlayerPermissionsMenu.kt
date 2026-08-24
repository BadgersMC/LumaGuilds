package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val targetPlayerName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.bedrock("menu.common.unknown_player")
        val content = lang.bedrock("bedrock.claim_player_permissions.content", "player" to targetPlayerName)

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.claim_player_permissions.title", "player" to targetPlayerName))
            .content(content)
            .button(lang.bedrock("bedrock.claim_player_permissions.button.back"))
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
