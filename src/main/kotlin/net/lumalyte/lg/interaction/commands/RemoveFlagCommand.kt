package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag
import net.lumalyte.lg.application.results.claim.flags.DisableClaimFlagResult
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.values.Flag
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

@CommandAlias("claim")
class RemoveFlagCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val disableClaimFlag: DisableClaimFlag by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("removeflag")
    @CommandPermission("lumaguilds.command.claim.removeflag")
    fun onRemoveFlag(player: Player, flag: Flag) {
        // Get the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId

        // Remove flag from the claim and notify player of result
        val message = when (disableClaimFlag.execute(flag, partition.claimId)) {
            is DisableClaimFlagResult.Success -> lang.msg(
                "command.claim.remove_flag.success",
                "flag" to getFlagName(playerId, flag),
                "claim" to getClaimName(playerId, claimId),
            )
            is DisableClaimFlagResult.DoesNotExist -> lang.msg(
                "command.claim.remove_flag.does_not_exist",
                "claim" to getClaimName(playerId, claimId),
                "flag" to getFlagName(playerId, flag),
            )
            is DisableClaimFlagResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is DisableClaimFlagResult.StorageError -> lang.msg("general.error")
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

    /**
     * Helper function to retrieve the name of the permission.
     */
    private fun getFlagName(playerId: UUID, flag: Flag): String {
        return lang.legacy(flag.nameKey)
    }
}
