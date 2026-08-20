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
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.results.player.tool.GivePlayerClaimToolResult
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class ClaimCommand : BaseCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val getPartitionByPosition: GetPartitionByPosition by inject()
    private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride by inject()
    private val getClaimDetails: GetClaimDetails by inject()
    private val givePlayerClaimTool: GivePlayerClaimTool by inject()

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

    fun isPlayerHasClaimPermission(player: Player, partition: Partition): Boolean {
        // Check if player has override
        val overrideResult = doesPlayerHaveClaimOverride.execute(player.uniqueId)
        when (overrideResult) {
            is DoesPlayerHaveClaimOverrideResult.Success -> if (overrideResult.hasOverride) return true
            is DoesPlayerHaveClaimOverrideResult.StorageError -> return false
        }

        // Check if player owns claim
        val claim = getClaimDetails.execute(partition.claimId) ?: return false
        if (player.uniqueId != claim.playerId) {
            player.sendMessage(lang.msg("command.common.no_claim_permission"))
            return false
        }
        return true
    }

}
