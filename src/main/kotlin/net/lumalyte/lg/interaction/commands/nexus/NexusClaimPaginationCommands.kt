package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.infrastructure.i18n.plain
import net.lumalyte.lg.interaction.commands.pageBounds
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * ACF-free paginated claim listings. These handlers must stay inactive until
 * the single Nexus command cutover; do not register alongside legacy ACF.
 */
@Command(name = "claimlist")
class NexusClaimListCommand(
    private val lang: LangService,
    private val listPlayerClaims: ListPlayerClaims,
    private val getClaimBlockCount: GetClaimBlockCount,
) {
    @Subcommand("")
    @Permission("lumaguilds.command.claimlist")
    @PlayerOnly
    fun list(@Context player: Player, @Arg(name = "page", required = false) page: Int? = null) {
        val claims = listPlayerClaims.execute(player.uniqueId)
        if (claims.isEmpty()) {
            player.sendMessage(lang.msg("command.claim_list.no_claims"))
            return
        }

        val currentPage = page ?: 1
        val bounds = pageBounds(claims.size, currentPage)
        if (bounds == null) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        val info = ChatInfoBuilder(lang, player.uniqueId, lang.msg("command.claim_list.header"))
        bounds.forEach { index ->
            val claim = claims[index]
            info.addIndexed(
                index,
                lang.msg(
                    "command.claim_list.row",
                    "claim" to claim.name.ifEmpty { claim.id.toString().take(7) },
                    "x" to claim.position.x,
                    "y" to claim.position.y,
                    "z" to claim.position.z,
                    "blocks" to getClaimBlockCount.execute(claim.id),
                ),
            )
        }
        player.sendMessage(info.createPaged(currentPage, ceil(claims.size / 10.0).toInt()))
    }
}

@Command(name = "claim")
class NexusClaimPartitionsCommand(
    private val lang: LangService,
    private val getPartitionByPosition: GetPartitionByPosition,
    private val getClaimPartitions: GetClaimPartitions,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("partitions")
    @Permission("lumaguilds.command.claim.partitions")
    @PlayerOnly
    fun partitions(@Context player: Player, @Arg(name = "page", required = false) page: Int? = null) {
        val partition = getPartitionByPosition.execute(player.location.toPosition3D(), player.world.uid)
        if (partition == null) {
            player.sendMessage(lang.msg("command.common.unknown_partition"))
            return
        }

        val partitions = getClaimPartitions.execute(partition.claimId)
        val currentPage = page ?: 1
        val bounds = pageBounds(partitions.size, currentPage)
        if (bounds == null) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        val claimName = getClaimDetails.execute(partition.claimId)?.name ?: lang.plain("general.name_error")
        val info = ChatInfoBuilder(
            lang,
            player.uniqueId,
            lang.msg("command.partitions.header", "claim" to claimName),
        )
        bounds.forEach { index ->
            val area = partitions[index].area
            info.addIndexed(
                index,
                lang.msg(
                    "command.partitions.row",
                    "lower_x" to area.lowerPosition2D.x,
                    "lower_z" to area.lowerPosition2D.z,
                    "upper_x" to area.upperPosition2D.x,
                    "upper_z" to area.upperPosition2D.z,
                ),
            )
        }
        player.sendMessage(info.createPaged(currentPage, ceil(partitions.size / 10.0).toInt()))
    }
}
