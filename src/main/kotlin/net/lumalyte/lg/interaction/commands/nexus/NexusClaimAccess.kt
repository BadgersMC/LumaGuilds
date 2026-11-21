package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.services.ClaimManagementAuthorizer
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import org.bukkit.entity.Player

/**
 * Constructor-injected equivalent of ClaimCommand's location and authorization helpers.
 * Preserve legacy silent failures on override storage errors and missing claim records.
 * Do not share a long-lived authorization result: permissions must be checked at use time.
 */
class NexusClaimAccess(
    private val lang: LangService,
    private val getPartitionByPosition: GetPartitionByPosition,
    private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride,
    private val getClaimDetails: GetClaimDetails,
    private val claimManagementAuthorizer: ClaimManagementAuthorizer,
) {
    fun partitionAtPlayer(player: Player): Partition? {
        val partition = getPartitionByPosition.execute(player.location.toPosition3D(), player.world.uid)
        if (partition == null) {
            player.sendMessage(lang.msg("command.common.unknown_partition"))
        }
        return partition
    }

    fun hasPermission(
        player: Player,
        partition: Partition,
        permission: RankPermission = RankPermission.MANAGE_CLAIMS,
    ): Boolean {
        when (val override = doesPlayerHaveClaimOverride.execute(player.uniqueId)) {
            is DoesPlayerHaveClaimOverrideResult.Success -> if (override.hasOverride) return true
            DoesPlayerHaveClaimOverrideResult.StorageError -> return false
        }

        val claim = getClaimDetails.execute(partition.claimId) ?: return false
        if (!claimManagementAuthorizer.hasPermission(player.uniqueId, claim, permission)) {
            player.sendMessage(lang.msg("command.common.no_claim_permission"))
            return false
        }
        return true
    }
}
