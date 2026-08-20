package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.ceil

@CommandAlias("claim")
class TrustListCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim by inject()
    private val getClaimDetails: GetClaimDetails by inject()
    private val getClaimPlayerPermissions: GetClaimPlayerPermissions by inject()

    @Subcommand("trustlist")
    @CommandPermission("lumaguilds.command.claim.trustlist")
    fun onTrustList(player: Player, @Default("1") page: Int) {
        // Gets the partition at the player's current location
        val partition = getPartitionAtPlayer(player) ?: return
        if (!isPlayerHasClaimPermission(player, partition)) return

        // Get players who have at least one permission in the claim
        val trustedPlayers = getPlayersWithPermissionInClaim.execute(partition.claimId)

        // Notify if claim has no trusted players
        if (trustedPlayers.isEmpty()) {
            player.sendMessage(lang.msg("command.claim.trust_list.no_players"))
            return
        }

        // Check if page is empty
        if (page * 10 - 9 > trustedPlayers.count() || page < 1) {
            player.sendMessage(lang.msg("command.common.invalid_page"))
            return
        }

        // Get names and sort alphabetically
        val trustedPlayerInfo = trustedPlayers.map { playerId ->
            val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
            offlinePlayer.let { playerId to it.name }
        }.sortedBy { it.second }

        // Generate chat output header
        val claimName = getClaimDetails.execute(partition.claimId)?.name ?: lang.legacy("general.name_error")
        val chatInfo = ChatInfoBuilder(
            lang,
            player.uniqueId,
            lang.legacy("command.claim.trust_list.header", "claim" to claimName),
        )

        // Output 5 players at a time per page
        val entries = trustedPlayerInfo.withIndex().toList().subList(0 + ((page - 1) * 5),
            (4 + ((page - 1) * 5)).coerceAtMost(trustedPlayers.size))
        val listSeparator = lang.legacy("general.list_separator")
        entries.forEach { (_, entry) ->
            val permissions = getClaimPlayerPermissions.execute(partition.claimId, entry.first)
            val row = lang.legacy(
                "command.claim.trust_list.row",
                "player" to entry.second,
                "permissions" to permissions.joinToString(listSeparator),
            )
            chatInfo.addRow(row)
        }
        player.sendMessage(chatInfo.createPaged(page, ceil(trustedPlayers.count() / 5.0).toInt()))
    }
}
