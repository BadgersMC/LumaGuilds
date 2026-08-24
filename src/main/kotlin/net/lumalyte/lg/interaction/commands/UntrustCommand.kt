package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.plain

import co.aikar.commands.annotation.*
import co.aikar.commands.bukkit.contexts.OnlinePlayer
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.results.claim.permission.RevokePlayerClaimPermissionResult
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.values.ClaimPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.getValue

@CommandAlias("claim")
class UntrustCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val revokePlayerClaimPermission: RevokePlayerClaimPermission by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("untrust")
    @CommandPermission("lumaguilds.command.claim.untrust")
    fun onUntrust(player: Player, targetPlayer: OnlinePlayer, permission: ClaimPermission) {
        // Get the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId
        val targetPlayerId = targetPlayer.player.uniqueId
        val targetPlayerName = targetPlayer.player.name

        // Execute action to revoke permission and fetch associated locale text
        val outcome = revokePlayerClaimPermission.execute(claimId, targetPlayerId, permission)
        val message = when (outcome) {
            is RevokePlayerClaimPermissionResult.Success -> lang.msg(
                "command.claim.untrust.success",
                "permission" to getPermissionName(playerId, permission),
                "player" to targetPlayerName,
                "claim" to getClaimName(playerId, claimId),
            )
            is RevokePlayerClaimPermissionResult.DoesNotExist -> lang.msg(
                "command.claim.untrust.does_not_exist",
                "player" to targetPlayerName,
                "permission" to getPermissionName(playerId, permission),
                "claim" to getClaimName(playerId, claimId),
            )
            is RevokePlayerClaimPermissionResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is RevokePlayerClaimPermissionResult.StorageError -> lang.msg("general.error")
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
