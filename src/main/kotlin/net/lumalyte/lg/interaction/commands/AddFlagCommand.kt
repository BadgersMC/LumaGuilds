package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.results.claim.flags.EnableClaimFlagResult
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.values.Flag
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

@CommandAlias("claim")
class AddFlagCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val enableClaimFlag: EnableClaimFlag by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("addflag")
    @CommandPermission("lumaguilds.command.claim.addflag")
    fun onAddFlag(player: Player, flag: Flag) {
        // Gets the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Assign common variables
        val claimId = partition.claimId
        val playerId = player.uniqueId

        // Add flag to the claim and notify player of result
        val message = when (enableClaimFlag.execute(flag, partition.claimId)) {
            EnableClaimFlagResult.Success -> lang.msg(
                "command.claim.add_flag.success",
                "flag" to getFlagName(playerId, flag),
                "claim" to getClaimName(playerId, claimId),
            )
            EnableClaimFlagResult.AlreadyExists -> lang.msg(
                "command.claim.add_flag.already_exists",
                "claim" to getClaimName(playerId, claimId),
                "flag" to getFlagName(playerId, flag),
            )
            EnableClaimFlagResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            EnableClaimFlagResult.StorageError -> lang.msg("general.error")
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
