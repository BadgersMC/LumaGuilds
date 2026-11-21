package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import net.lumalyte.lg.infrastructure.i18n.plain
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * Inactive Nexus equivalent of the ACF /claim trustlist handler. Do not register alongside legacy ACF.
 *
 * Deliberate LEGACY QUIRKS preserved (see TrustListCommand); do not "fix" without a spec change:
 *  - page validity uses a 10-per-page rule: valid iff page >= 1 && page * 10 - 9 <= trusted count;
 *  - rows start at (page - 1) * 5 but the exclusive end is start + 4, so only 4 rows show per page
 *    and the 5th entry of each block (sorted index 4, 9, 14, ...) is never displayed;
 *  - the footer reports ceil(count / 5.0) total pages, inconsistent with the validity rule;
 *  - entries sort by offline-player name, null names first.
 *
 * Only intentional divergence: legacy computes page * 10 in Int, so huge pages overflow and throw
 * (subList IllegalArgumentException/IndexOutOfBounds). Here the math is Long and such pages
 * reply with command.common.invalid_page instead.
 */
@Command(name = "claim")
class NexusClaimTrustListCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim,
    private val getClaimDetails: GetClaimDetails,
    private val getClaimPlayerPermissions: GetClaimPlayerPermissions,
) {
    @Subcommand("trustlist")
    @Permission("lumaguilds.command.claim.trustlist")
    @PlayerOnly
    fun trustList(@Context player: Player, @Arg(name = "page", required = false) page: Int? = null) {
        val currentPage = page ?: 1
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)) return

        val trustedPlayers = getPlayersWithPermissionInClaim.execute(partition.claimId)
        if (trustedPlayers.isEmpty()) {
            player.sendMessage(lang.msg("command.claim.trust_list.no_players"))
            return
        }

        if (currentPage < 1 || currentPage.toLong() * 10L - 9L > trustedPlayers.size.toLong()) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        val trustedPlayerInfo = trustedPlayers
            .map { playerId -> playerId to Bukkit.getOfflinePlayer(playerId).name }
            .sortedBy { it.second }

        val claimName = getClaimDetails.execute(partition.claimId)?.name ?: lang.plain("general.name_error")
        val chatInfo = ChatInfoBuilder(
            lang,
            player.uniqueId,
            lang.msg("command.claim.trust_list.header", "claim" to claimName),
        )

        val start = (currentPage - 1) * 5
        val end = (start + 4).coerceAtMost(trustedPlayers.size)
        val listSeparator = lang.plain("general.list_separator")
        trustedPlayerInfo.subList(start, end).forEach { (playerId, name) ->
            val permissions = getClaimPlayerPermissions.execute(partition.claimId, playerId)
            chatInfo.addRow(
                lang.msg(
                    "command.claim.trust_list.row",
                    "player" to name,
                    "permissions" to permissions.joinToString(listSeparator),
                ),
            )
        }
        player.sendMessage(chatInfo.createPaged(currentPage, ceil(trustedPlayers.size / 5.0).toInt()))
    }
}
