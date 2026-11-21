package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimNameResult
import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.ChatInfoBuilder
import net.lumalyte.lg.infrastructure.i18n.plain
import net.lumalyte.lg.infrastructure.i18n.localizedClaimFlagNames
import net.lumalyte.lg.infrastructure.i18n.localizedClaimPermissionNames
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Inactive Nexus equivalents for the ACF /claim info and /claim rename handlers.
 * Share the live-access checks without retaining authorization across invocations.
 */
@Command(name = "claim")
class NexusClaimInfoCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val getClaimDetails: GetClaimDetails,
    private val getClaimFlags: GetClaimFlags,
    private val getClaimPermissions: GetClaimPermissions,
    private val getClaimPartitions: GetClaimPartitions,
    private val getClaimBlockCount: GetClaimBlockCount,
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim,
) {
    @Subcommand("info")
    @Permission("lumaguilds.command.claim.info")
    @PlayerOnly
    fun info(@Context player: Player) {
        val partition = access.partitionAtPlayer(player) ?: return
        val claimId = partition.claimId
        val claim = getClaimDetails.execute(claimId) ?: return

        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault())

        val chatInfo = ChatInfoBuilder(
            lang, player.uniqueId,
            lang.msg("command.claim.info.header", "claim" to claim.name),
        )
        if (claim.description.isNotEmpty()) chatInfo.addParagraph("${claim.description}\n")

        val ownerName = Bukkit.getOfflinePlayer(claim.playerId).name ?: lang.msg("general.name_error")
        chatInfo.addRow(lang.msg("command.claim.info.row.owner", "owner" to ownerName))
        chatInfo.addRow(lang.msg("command.claim.info.row.creation_date", "creation_date" to dateTimeFormatter.format(claim.creationTime)))
        chatInfo.addRow(lang.msg("command.claim.info.row.partition_count", "partition_count" to getClaimPartitions.execute(claimId).count().toString()))
        chatInfo.addRow(lang.msg("command.claim.info.row.block_count", "block_count" to getClaimBlockCount.execute(claimId).toString()))
        chatInfo.addRow(lang.msg("command.claim.info.row.flags", "flags" to lang.localizedClaimFlagNames(getClaimFlags.execute(claimId))))
        chatInfo.addRow(lang.msg("command.claim.info.row.default_permissions", "permissions" to lang.localizedClaimPermissionNames(getClaimPermissions.execute(claimId))))
        chatInfo.addRow(lang.msg("command.claim.info.row.trusted_users", "trusted_users" to getPlayersWithPermissionInClaim.execute(claimId).count().toString()))
        chatInfo.addSpace()
        player.sendMessage(chatInfo.create())
    }
}

@Command(name = "claim")
class NexusClaimRenameCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val updateClaimName: UpdateClaimName,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("rename")
    @Permission("lumaguilds.command.claim.rename")
    @PlayerOnly
    fun rename(@Context player: Player, @Arg(name = "name") name: String) {
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS)) return

        val claimId = partition.claimId
        val message = when (val result = updateClaimName.execute(claimId, name)) {
            is UpdateClaimNameResult.Success -> lang.msg(
                "command.claim.rename.success",
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
                "name" to name,
            )
            UpdateClaimNameResult.NameAlreadyExists -> lang.msg(
                "command.claim.rename.already_exists", "name" to name,
            )
            UpdateClaimNameResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is UpdateClaimNameResult.InputTextInvalid -> {
                when (val firstError = result.errors.firstOrNull()) {
                    is TextValidationErrorResult.ExceededCharacterLimit -> lang.msg(
                        "command.claim.rename.exceed_limit",
                        "length" to name.count(),
                        "limit" to firstError.maxCharacters,
                    )
                    is TextValidationErrorResult.InvalidCharacters -> lang.msg(
                        "command.claim.rename.invalid_character", "characters" to firstError.invalidCharacters,
                    )
                    is TextValidationErrorResult.ContainsBlacklistedWord -> lang.msg(
                        "command.claim.rename.blacklisted_word", "word" to firstError.blacklistedWord,
                    )
                    is TextValidationErrorResult.NoCharactersProvided -> lang.msg("command.claim.rename.blank")
                    null -> lang.msg("general.error")
                }
            }
            UpdateClaimNameResult.StorageError -> lang.msg("general.error")
        }

        player.sendMessage(message)
    }
}
