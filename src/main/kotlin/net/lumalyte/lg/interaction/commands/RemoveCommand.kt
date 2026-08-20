package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.RemovePartition
import net.lumalyte.lg.application.results.claim.partition.RemovePartitionResult
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.getValue

@CommandAlias("claim")
class RemoveCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val removePartition: RemovePartition by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("remove")
    @CommandPermission("lumaguilds.command.claim.remove")
    fun onRemove(player: Player) {
        // Get the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId

        // Remove flag from the claim and notify player of result
        val message = when (removePartition.execute(partition.id)) {
            is RemovePartitionResult.Success -> lang.msg(
                "command.claim.remove.success",
                "claim" to getClaimName(playerId, claimId),
            )
            RemovePartitionResult.DoesNotExist -> lang.msg(
                "command.claim.remove.unknown_partition",
                "claim" to getClaimName(playerId, claimId),
            )
            RemovePartitionResult.Disconnected -> lang.msg("command.claim.remove.disconnected")
            RemovePartitionResult.ExposedClaimAnchor -> lang.msg("command.claim.remove.exposed_anchor")
            RemovePartitionResult.StorageError -> lang.msg("general.error")
        }

        // Output to player chat
        player.sendMessage(message)
    }

    /**
     * Helper function to retrieve the claim name or a default error message if not found.
     */
    private fun getClaimName(playerId: UUID, claimId: UUID): String {
        return getClaimDetails.execute(claimId)?.name ?: lang.legacy("general.name_error")
    }
}
