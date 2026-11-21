package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag
import net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription
import net.lumalyte.lg.application.results.claim.flags.DisableClaimFlagResult
import net.lumalyte.lg.application.results.claim.flags.EnableClaimFlagResult
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimAttributeResult
import net.lumalyte.lg.application.results.common.TextValidationErrorResult
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Flag
import net.lumalyte.lg.infrastructure.i18n.localizedClaimFlagPlain
import net.lumalyte.lg.infrastructure.i18n.plain
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Inactive Nexus-native claim mutations. Never register alongside ACF before cutover.
 * All authorization checks execute per request and before state mutation.
 */
@Command(name = "claim")
class NexusClaimDescriptionCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val update: UpdateClaimDescription,
    private val details: GetClaimDetails,
) {
    @Subcommand("description")
    @Permission("lumaguilds.command.claim.description")
    @PlayerOnly
    fun description(@Context player: Player, @Arg(name = "description") @Greedy description: String) {
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_CLAIMS)) return

        val id = partition.claimId
        val message = when (val result = update.execute(id, description)) {
            is UpdateClaimAttributeResult.Success -> lang.msg(
                "command.claim.description.success",
                "claim" to claimName(lang, details, id),
            )
            UpdateClaimAttributeResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is UpdateClaimAttributeResult.InputTextInvalid -> when (val error = result.errors.firstOrNull()) {
                is TextValidationErrorResult.ExceededCharacterLimit -> lang.msg(
                    "command.claim.description.exceed_limit",
                    "length" to description.count(),
                    "limit" to error.maxCharacters,
                )
                is TextValidationErrorResult.InvalidCharacters -> lang.msg(
                    "command.claim.description.invalid_character", "characters" to error.invalidCharacters,
                )
                is TextValidationErrorResult.ContainsBlacklistedWord -> lang.msg(
                    "command.claim.description.blacklisted_word", "word" to error.blacklistedWord,
                )
                TextValidationErrorResult.NoCharactersProvided -> lang.msg("command.claim.description.blank")
                null -> lang.msg("general.error")
            }
            UpdateClaimAttributeResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

@Command(name = "claim")
class NexusClaimAddFlagCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val enable: EnableClaimFlag,
    private val details: GetClaimDetails,
) {
    @Subcommand("addflag")
    @Permission("lumaguilds.command.claim.addflag")
    @PlayerOnly
    fun addFlag(@Context player: Player, @Arg(name = "flag") flagName: String) {
        val flag = parseFlag(flagName) ?: run {
            player.sendMessage(lang.msg("general.error"))
            return
        }
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_FLAGS)) return

        val id = partition.claimId
        val message = when (enable.execute(flag, id)) {
            EnableClaimFlagResult.Success -> lang.msg(
                "command.claim.add_flag.success",
                "flag" to lang.localizedClaimFlagPlain(flag),
                "claim" to claimName(lang, details, id),
            )
            EnableClaimFlagResult.AlreadyExists -> lang.msg(
                "command.claim.add_flag.already_exists",
                "claim" to claimName(lang, details, id),
                "flag" to lang.localizedClaimFlagPlain(flag),
            )
            EnableClaimFlagResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            EnableClaimFlagResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

@Command(name = "claim")
class NexusClaimRemoveFlagCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val disable: DisableClaimFlag,
    private val details: GetClaimDetails,
) {
    @Subcommand("removeflag")
    @Permission("lumaguilds.command.claim.removeflag")
    @PlayerOnly
    fun removeFlag(@Context player: Player, @Arg(name = "flag") flagName: String) {
        val flag = parseFlag(flagName) ?: run {
            player.sendMessage(lang.msg("general.error"))
            return
        }
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_FLAGS)) return

        val id = partition.claimId
        val message = when (disable.execute(flag, id)) {
            DisableClaimFlagResult.Success -> lang.msg(
                "command.claim.remove_flag.success",
                "flag" to lang.localizedClaimFlagPlain(flag),
                "claim" to claimName(lang, details, id),
            )
            DisableClaimFlagResult.DoesNotExist -> lang.msg(
                "command.claim.remove_flag.does_not_exist",
                "claim" to claimName(lang, details, id),
                "flag" to lang.localizedClaimFlagPlain(flag),
            )
            DisableClaimFlagResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            DisableClaimFlagResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

/** Nexus Paper currently has no built-in enum resolver. Parse the finite Flag enum case-insensitively. */
private fun parseFlag(value: String): Flag? =
    Flag.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

private fun claimName(lang: LangService, details: GetClaimDetails, id: UUID): String =
    details.execute(id)?.name ?: lang.plain("general.name_error")
