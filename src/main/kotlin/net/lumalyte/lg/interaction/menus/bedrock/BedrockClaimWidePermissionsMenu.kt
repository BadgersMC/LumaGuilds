package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.application.results.claim.permission.GrantClaimWidePermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokeClaimWidePermissionResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition claim wide permissions menu using Cumulus SimpleForm
 * Manages default/public permissions for the claim
 */
class BedrockClaimWidePermissionsMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val claim: Claim,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val lang: LangService by inject()
    private val getClaimPermissions: GetClaimPermissions by inject()
    private val grantClaimWidePermission: GrantClaimWidePermission by inject()
    private val revokeClaimWidePermission: RevokeClaimWidePermission by inject()

    override fun getForm(): Form {
        val current = getClaimPermissions.execute(claim.id).toSet()
        val editor = BedrockClaimPermissionEditor(
            grantWide = { claimId, permission ->
                when (grantClaimWidePermission.execute(claimId, permission)) {
                    GrantClaimWidePermissionResult.Success, GrantClaimWidePermissionResult.AlreadyExists -> true
                    else -> false
                }
            },
            revokeWide = { claimId, permission ->
                when (revokeClaimWidePermission.execute(claimId, permission)) {
                    RevokeClaimWidePermissionResult.Success, RevokeClaimWidePermissionResult.DoesNotExist -> true
                    else -> false
                }
            },
            grantPlayer = { _, _, _ -> true },
            revokePlayer = { _, _, _ -> true }
        )
        val builder = CustomForm.builder()
            .title(lang.bedrock("bedrock.claim_wide_permissions.title", "claim" to claim.name))
        ClaimPermission.entries.forEach { permission ->
            builder.toggle(permission.bedrockLabel(lang), permission in current)
        }
        return builder.validResultHandler { response ->
                val submitted = ClaimPermission.entries.filterIndexed { index, _ -> response.asToggle(index) }.toSet()
                if (editor.saveWide(claim.id, current, submitted)) {
                    player.sendMessage(lang.msg("bedrock.claim_wide_permissions.saved"))
                    bedrockNavigator.goBack()
                } else {
                    player.sendMessage(lang.msg("bedrock.claim_wide_permissions.save_failed"))
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
