package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import org.bukkit.entity.Player
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import org.bukkit.Bukkit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@CommandAlias("claim")
class InfoCommand : ClaimCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val getClaimDetails: GetClaimDetails by inject()
    private val getClaimFlags: GetClaimFlags by inject()
    private val getClaimPermissions: GetClaimPermissions by inject()
    private val getClaimPartitions: GetClaimPartitions by inject()
    private val getClaimBlockCount: GetClaimBlockCount by inject()
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim by inject()

    @Subcommand("info")
    @CommandPermission("lumaguilds.command.claim.info")
    fun onInfo(player: Player) {
        // Get partition at current location with associated claim
        val partition = getPartitionAtPlayer(player) ?: return
        val claimId = partition.claimId
        val claim = getClaimDetails.execute(claimId) ?: return

        // Format datetime for creation date
        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault())

        // Add header and description
        val chatInfo = ChatInfoBuilder(lang, player.uniqueId,
            lang.msg("command.claim.info.header", "claim" to claim.name))
        if (claim.description.isNotEmpty()) chatInfo.addParagraph("${claim.description}\n")

        // Add metadata values
        val ownerName = Bukkit.getOfflinePlayer(player.uniqueId).name ?: lang.msg("general.name_error")
        chatInfo.addRow(lang.msg("command.claim.info.row.owner", "owner" to ownerName))
        chatInfo.addRow(lang.msg("command.claim.info.row.creation_date", "creation_date" to dateTimeFormatter.format(claim.creationTime)))
        chatInfo.addRow(lang.msg("command.claim.info.row.partition_count", "partition_count" to getClaimPartitions.execute(claimId).count().toString()))
        chatInfo.addRow(lang.msg("command.claim.info.row.block_count", "block_count" to getClaimBlockCount.execute(claimId).toString()))
        chatInfo.addRow(lang.msg("command.claim.info.row.flags", "flags" to getClaimFlags.execute(claimId).map { lang.msg(it.nameKey) }))
        chatInfo.addRow(lang.msg("command.claim.info.row.default_permissions", "permissions" to getClaimPermissions.execute(claimId).map { lang.msg(it.nameKey) }))
        chatInfo.addRow(lang.msg("command.claim.info.row.trusted_users", "trusted_users" to getPlayersWithPermissionInClaim.execute(claimId).count().toString()))
        chatInfo.addSpace()

        // Output to player
        player.sendMessage(chatInfo.create())
    }
}
