package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.plain

import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.application.results.claim.permission.RevokeClaimWidePermissionResult
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.values.ClaimPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

@CommandAlias("claim")
class UntrustAllCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val revokeClaimWidePermission: RevokeClaimWidePermission by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("untrustall")
    @CommandPermission("lumaguilds.command.claim.untrustall")
    fun onUntrustAll(player: Player, permission: ClaimPermission) {
        // Gets the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId

        // Execute action to revoke claim wide permissions and fetch associated locale text
        val outcome = revokeClaimWidePermission.execute(claimId, permission)
        val message = when (outcome) {
            is RevokeClaimWidePermissionResult.Success -> lang.msg(
                "command.claim.untrust_all.success",
                "permission" to getPermissionName(playerId, permission),
                "claim" to getClaimName(playerId, claimId),
            )
            is RevokeClaimWidePermissionResult.DoesNotExist -> lang.msg(
                "command.claim.untrust_all.does_not_exist",
                "claim" to getClaimName(playerId, claimId),
                "permission" to getPermissionName(playerId, permission),
            )
            is RevokeClaimWidePermissionResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is RevokeClaimWidePermissionResult.StorageError -> lang.msg("general.error")
        }

        // Output to player chat
        player.sendMessage(message)
    }

    /**
     * Helper function to retrieve the claim name or a default error message if not found.
     */
    private fun getClaimName(playerId: UUID, claimId: UUID): String {
        return getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")
    }

    /**
     * Helper function to retrieve the name of the permission.
     */
    private fun getPermissionName(playerId: UUID, permission: ClaimPermission): String {
        return lang.plain(permission.nameKey)
    }
}
