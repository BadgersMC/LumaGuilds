package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Syntax
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool
import net.lumalyte.lg.application.services.ClaimManagementAuthorizer
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.results.player.tool.GivePlayerClaimToolResult
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D


/**
 * Explicit lazy collaborators shared by [ClaimCommand] and its ACF subcommand classes (REQ-NEXUS-DI-1..3).
 * Each resolves on first use, so claim services stay untouched until a claim command runs.
 */
data class ClaimCommandDependencies(
    val lang: Lazy<LangService>,
    val getPartitionByPosition: Lazy<GetPartitionByPosition>,
    val doesPlayerHaveClaimOverride: Lazy<DoesPlayerHaveClaimOverride>,
    val getClaimDetails: Lazy<GetClaimDetails>,
    val givePlayerClaimTool: Lazy<GivePlayerClaimTool>,
    val claimManagementAuthorizer: Lazy<ClaimManagementAuthorizer>,
)

open class ClaimCommand(
    claimDependencies: ClaimCommandDependencies,
) : BaseCommand() {
    private val lang: LangService by claimDependencies.lang
    private val getPartitionByPosition: GetPartitionByPosition by claimDependencies.getPartitionByPosition
    private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride by claimDependencies.doesPlayerHaveClaimOverride
    private val getClaimDetails: GetClaimDetails by claimDependencies.getClaimDetails
    private val givePlayerClaimTool: GivePlayerClaimTool by claimDependencies.givePlayerClaimTool
    private val claimManagementAuthorizer: ClaimManagementAuthorizer by claimDependencies.claimManagementAuthorizer

    @CommandAlias("claim")
    @CommandPermission("lumaguilds.command.claim")
    @Syntax("claim")
    fun onClaim(player: Player) {
        when (givePlayerClaimTool.execute(player.uniqueId)) {
            GivePlayerClaimToolResult.PlayerAlreadyHasTool -> player.sendMessage(lang.msg("command.claim.already_have_tool"))
            GivePlayerClaimToolResult.Success -> player.sendMessage(lang.msg("command.claim.success"))
            GivePlayerClaimToolResult.PlayerNotFound -> player.sendMessage(lang.msg("general.error"))
        }
    }

    fun getPartitionAtPlayer(player: Player): Partition? {
        val claimPartition = getPartitionByPosition.execute(player.location.toPosition3D(), player.world.uid)
        if (claimPartition == null) {
            player.sendMessage(lang.msg("command.common.unknown_partition"))
            return null
        }
        return claimPartition
    }

    fun isPlayerHasClaimPermission(
        player: Player,
        partition: Partition,
        permission: RankPermission = RankPermission.MANAGE_CLAIMS,
    ): Boolean {
        // Check if player has override
        val overrideResult = doesPlayerHaveClaimOverride.execute(player.uniqueId)
        when (overrideResult) {
            is DoesPlayerHaveClaimOverrideResult.Success -> if (overrideResult.hasOverride) return true
            is DoesPlayerHaveClaimOverrideResult.StorageError -> return false
        }

        // Personal owners remain authorized; guild-owned claims also use current rank permissions.
        val claim = getClaimDetails.execute(partition.claimId) ?: return false
        if (!claimManagementAuthorizer.hasPermission(player.uniqueId, claim, permission)) {
            player.sendMessage(lang.msg("command.common.no_claim_permission"))
            return false
        }
        return true
    }

}
