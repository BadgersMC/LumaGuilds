package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.results.claim.permission.GrantPlayerClaimPermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokePlayerClaimPermissionResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
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
    private val getClaimPlayerPermissions: GetClaimPlayerPermissions by inject()
    private val grantPlayerClaimPermission: GrantPlayerClaimPermission by inject()
    private val revokePlayerClaimPermission: RevokePlayerClaimPermission by inject()

    override fun getForm(): Form {
        val targetPlayerName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.bedrock("menu.common.unknown_player")
        val current = getClaimPlayerPermissions.execute(claim.id, targetPlayerId).toSet()
        val editor = BedrockClaimPermissionEditor(
            grantWide = { _, _ -> true },
            revokeWide = { _, _ -> true },
            grantPlayer = { claimId, playerId, permission ->
                when (grantPlayerClaimPermission.execute(claimId, playerId, permission)) {
                    GrantPlayerClaimPermissionResult.Success, GrantPlayerClaimPermissionResult.AlreadyExists -> true
                    else -> false
                }
            },
            revokePlayer = { claimId, playerId, permission ->
                when (revokePlayerClaimPermission.execute(claimId, playerId, permission)) {
                    RevokePlayerClaimPermissionResult.Success, RevokePlayerClaimPermissionResult.DoesNotExist -> true
                    else -> false
                }
            }
        )
        val builder = CustomForm.builder()
            .title(lang.bedrock("bedrock.claim_player_permissions.title", "player" to targetPlayerName))
        ClaimPermission.entries.forEach { permission ->
            builder.toggle(permission.bedrockLabel(lang), permission in current)
        }
        return builder.validResultHandler { response ->
                val submitted = ClaimPermission.entries.filterIndexed { index, _ -> response.asToggle(index) }.toSet()
                if (editor.savePlayer(claim.id, targetPlayerId, current, submitted)) {
                    player.sendMessage(lang.msg("bedrock.claim_player_permissions.saved"))
                    bedrockNavigator.goBack()
                } else {
                    player.sendMessage(lang.msg("bedrock.claim_player_permissions.save_failed"))
                    open()
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
