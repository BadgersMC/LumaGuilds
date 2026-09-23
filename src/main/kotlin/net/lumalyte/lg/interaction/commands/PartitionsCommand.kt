package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.plain

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import org.bukkit.entity.Player
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.math.ceil

@CommandAlias("claim")
class PartitionsCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val getClaimPartitions: GetClaimPartitions by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    @Subcommand("partitions")
    @CommandPermission("lumaguilds.command.claim.partitions")
    fun onPartitions(player: Player, @Default("1") page: Int) {
        // Get the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return

        // Get partitions of claim
        val partitions = getClaimPartitions.execute(partition.claimId)

        val bounds = pageBounds(partitions.size, page)
        if (bounds == null) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        // Output list of partitions
        val claimName = getClaimName(player.uniqueId, partition.claimId)
        val header = lang.msg("command.partitions.header", "claim" to claimName)
        val chatInfo = ChatInfoBuilder(lang, player.uniqueId, header)
        bounds.forEach { index ->
            chatInfo.addIndexed(index, lang.msg("command.partitions.row",
                "lower_x" to partitions[index].area.lowerPosition2D.x,
                "lower_z" to partitions[index].area.lowerPosition2D.z,
                "upper_x" to partitions[index].area.upperPosition2D.x,
                "upper_z" to partitions[index].area.upperPosition2D.z,
            ))
        }
        player.sendMessage(chatInfo.createPaged(page, ceil((partitions.count() / 10.0)).toInt()))
    }

    /**
     * Helper function to retrieve the claim name or a default error message if not found.
     */
    private fun getClaimName(playerId: UUID, claimId: UUID): String {
        return getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")
    }
}
