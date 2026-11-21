package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.RemovePartition
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.results.claim.partition.RemovePartitionResult
import net.lumalyte.lg.application.results.player.ToggleClaimOverrideResult
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.plain
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Inactive Nexus equivalents for the ACF /claim remove and /claimoverride handlers.
 * Authorization is evaluated per invocation through [NexusClaimAccess].
 */
@Command(name = "claim")
class NexusClaimRemoveCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val removePartition: RemovePartition,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("remove")
    @Permission("lumaguilds.command.claim.remove")
    @PlayerOnly
    fun remove(@Context player: Player) {
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.DELETE_CLAIMS)) return

        val claimId = partition.claimId
        val message = when (removePartition.execute(partition.id)) {
            is RemovePartitionResult.Success -> lang.msg(
                "command.claim.remove.success", "claim" to claimName(claimId),
            )
            RemovePartitionResult.DoesNotExist -> lang.msg(
                "command.claim.remove.unknown_partition", "claim" to claimName(claimId),
            )
            RemovePartitionResult.Disconnected -> lang.msg("command.claim.remove.disconnected")
            RemovePartitionResult.ExposedClaimAnchor -> lang.msg("command.claim.remove.exposed_anchor")
            RemovePartitionResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }

    private fun claimName(claimId: UUID): String =
        getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")
}

@Command(name = "claimoverride")
class NexusClaimOverrideCommand(
    private val lang: LangService,
    private val toggleClaimOverride: ToggleClaimOverride,
) {
    @Subcommand("")
    @Permission("lumaguilds.command.claimoverride")
    @PlayerOnly
    fun toggle(@Context player: Player) {
        val message = when (val result = toggleClaimOverride.execute(player.uniqueId)) {
            is ToggleClaimOverrideResult.Success ->
                if (result.isOverrideEnabled) lang.msg("command.claim_override.enabled")
                else lang.msg("command.claim_override.disabled")
            ToggleClaimOverrideResult.PlayerNotFound,
            ToggleClaimOverrideResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}
