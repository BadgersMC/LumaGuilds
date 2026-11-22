package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.application.results.claim.permission.GrantClaimWidePermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokeClaimWidePermissionResult
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ClaimPermission
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.infrastructure.i18n.localizedClaimPermissionNames
import net.lumalyte.lg.infrastructure.i18n.plain
import org.bukkit.entity.Player

/**
 * Finite permission lookup with the legacy enum resolver's case/punctuation normalization.
 * Partial prefixes are rejected.
 */
internal fun parseClaimPermission(raw: String): ClaimPermission? =
    ClaimPermission.entries.firstOrNull { normalizedClaimEnumName(it.name) == normalizedClaimEnumName(raw) }

/**
 * Inactive Nexus equivalent of the ACF /claim trustall handler.
 * MANAGE_PERMISSIONS is checked live, immediately before the mutation.
 * Operation-specific completions preserve permission filtering. Invalid values still reply with
 * general.error rather than the former framework's built-in invalid-argument diagnostic.
 */
@Command(name = "claim")
class NexusTrustAllCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val grantClaimWidePermission: GrantClaimWidePermission,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("trustall")
    @Permission("lumaguilds.command.claim.trustall")
    @PlayerOnly
    fun trustAll(
        @Context player: Player,
        @Arg(name = "permission") @Suggests("claim.permissions.trustall") permission: ClaimArgumentToken,
    ) = trustAll(player, permission.value)

    fun trustAll(player: Player, permissionName: String) {
        val permission = parseClaimPermission(permissionName)
        if (permission == null) {
            player.sendMessage(lang.msg("general.error"))
            return
        }
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)) return

        val claimId = partition.claimId
        val message = when (grantClaimWidePermission.execute(claimId, permission)) {
            is GrantClaimWidePermissionResult.Success -> lang.msg(
                "command.claim.trust_all.success",
                "permission" to permissionName(lang, permission),
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
            )
            is GrantClaimWidePermissionResult.AlreadyExists -> lang.msg(
                "command.claim.trust_all.already_exists",
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
                "permission" to permissionName(lang, permission),
            )
            is GrantClaimWidePermissionResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is GrantClaimWidePermissionResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

/**
 * Inactive Nexus equivalent of the ACF /claim untrustall handler.
 * Same parity gap as [NexusTrustAllCommand].
 */
@Command(name = "claim")
class NexusUntrustAllCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val revokeClaimWidePermission: RevokeClaimWidePermission,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("untrustall")
    @Permission("lumaguilds.command.claim.untrustall")
    @PlayerOnly
    fun untrustAll(
        @Context player: Player,
        @Arg(name = "permission") @Suggests("claim.permissions.untrustall") permission: ClaimArgumentToken,
    ) = untrustAll(player, permission.value)

    fun untrustAll(player: Player, permissionName: String) {
        val permission = parseClaimPermission(permissionName)
        if (permission == null) {
            player.sendMessage(lang.msg("general.error"))
            return
        }
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)) return

        val claimId = partition.claimId
        val message = when (revokeClaimWidePermission.execute(claimId, permission)) {
            is RevokeClaimWidePermissionResult.Success -> lang.msg(
                "command.claim.untrust_all.success",
                "permission" to permissionName(lang, permission),
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
            )
            is RevokeClaimWidePermissionResult.DoesNotExist -> lang.msg(
                "command.claim.untrust_all.does_not_exist",
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
                "permission" to permissionName(lang, permission),
            )
            is RevokeClaimWidePermissionResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is RevokeClaimWidePermissionResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

/** Reuses the declared finite-enum lookup so the dynamic locale baseline is unchanged. */
private fun permissionName(lang: LangService, permission: ClaimPermission): String =
    PlainTextComponentSerializer.plainText()
        .serialize(lang.localizedClaimPermissionNames(listOf(permission)).single())
