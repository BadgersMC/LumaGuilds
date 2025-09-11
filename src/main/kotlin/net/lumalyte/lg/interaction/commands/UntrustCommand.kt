package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.*
import co.aikar.commands.bukkit.contexts.OnlinePlayer
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.results.claim.permission.RevokePlayerClaimPermissionResult
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.LocalizationKeys
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.getValue

@CommandAlias("claim")
class UntrustCommand : ClaimCommand(), KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
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
        val targetPlayerName = targetPlayer.player.displayName()

        // Execute action to revoke permission and fetch associated locale text
        val outcome = revokePlayerClaimPermission.execute(claimId, targetPlayerId, permission)
        val (messageKey, messageArgs) = when (outcome) {
            is RevokePlayerClaimPermissionResult.Success -> Pair(
                LocalizationKeys.COMMAND_CLAIM_UNTRUST_SUCCESS,
                arrayOf(getPermissionName(playerId, permission), targetPlayerName, getClaimName(playerId, claimId))
            )
            is RevokePlayerClaimPermissionResult.DoesNotExist -> Pair(
                LocalizationKeys.COMMAND_CLAIM_UNTRUST_DOES_NOT_EXIST,
                arrayOf(targetPlayerName, permission, getClaimName(playerId, claimId))
            )
            is RevokePlayerClaimPermissionResult.ClaimNotFound -> Pair(
                LocalizationKeys.COMMAND_COMMON_UNKNOWN_CLAIM,
                emptyArray<String>()
            )
            is RevokePlayerClaimPermissionResult.StorageError -> Pair(
                LocalizationKeys.GENERAL_ERROR,
                emptyArray<String>()
            )
        }

        // Output to player chat
        player.sendMessage(localizationProvider.get(player.uniqueId, messageKey, *messageArgs))
    }

    /**
     * Helper function to retrieve the claim name or a default error message if not found.
     */
    private fun getClaimName(playerId: UUID, claimId: UUID): String {
        return getClaimDetails.execute(claimId)?.name ?: localizationProvider.get(
            playerId, LocalizationKeys.GENERAL_NAME_ERROR
        )
    }

    /**
     * Helper function to retrieve the name of the permission.
     */
    private fun getPermissionName(playerId: UUID, permission: ClaimPermission): String {
        return localizationProvider.get(playerId, permission.nameKey)
    }
}
