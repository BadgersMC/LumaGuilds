package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.results.claim.permission.GrantPlayerClaimPermissionResult
import net.lumalyte.lg.application.results.claim.permission.RevokePlayerClaimPermissionResult
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.infrastructure.i18n.localizedClaimPermissionNames
import net.lumalyte.lg.infrastructure.i18n.plain
import org.bukkit.entity.Player

/**
 * Nexus handler for /claim trust.
 *
 * Target lookup uses the server's exact/partial name matcher and preserves visibility, ambiguity
 * and explicit vanished-player confirmation. Selectors are never interpreted. Unresolved targets
 * and invalid permission values retain general.error rather than the former framework's built-in
 * diagnostics. A target that resolves but is no longer online is rejected before claim lookup.
 * MANAGE_PERMISSIONS is checked live, immediately before the mutation.
 */
@Command(name = "claim")
class NexusTrustCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val grantPlayerClaimPermission: GrantPlayerClaimPermission,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("trust")
    @Permission("lumaguilds.command.claim.trust")
    @PlayerOnly
    fun trust(
        @Context player: Player,
        @Arg(name = "player") @Suggests("claim.players.trust") target: ClaimArgumentToken,
        @Arg(name = "permission") @Suggests("claim.permissions.trust") permission: ClaimArgumentToken,
    ) {
        val resolved = resolveClaimPlayer(player, target, lang) ?: return
        trust(player, resolved, permission.value)
    }

    fun trust(
        player: Player,
        target: Player,
        permissionName: String,
    ) {
        val permission = parseClaimPermission(permissionName)
        if (permission == null || !target.isOnline) {
            player.sendMessage(lang.msg("general.error"))
            return
        }
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)) return

        val claimId = partition.claimId
        val targetName = target.name
        val message = when (grantPlayerClaimPermission.execute(claimId, target.uniqueId, permission)) {
            is GrantPlayerClaimPermissionResult.Success -> lang.msg(
                "command.claim.trust.success",
                "permission" to playerPermissionName(lang, permission),
                "player" to targetName,
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
            )
            is GrantPlayerClaimPermissionResult.AlreadyExists -> lang.msg(
                "command.claim.trust.already_exists",
                "player" to targetName,
                "permission" to playerPermissionName(lang, permission),
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
            )
            is GrantPlayerClaimPermissionResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is GrantPlayerClaimPermissionResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

/**
 * Nexus handler for /claim untrust. Same target lookup and parity
 * gaps as [NexusTrustCommand].
 */
@Command(name = "claim")
class NexusUntrustCommand(
    private val lang: LangService,
    private val access: NexusClaimAccess,
    private val revokePlayerClaimPermission: RevokePlayerClaimPermission,
    private val getClaimDetails: GetClaimDetails,
) {
    @Subcommand("untrust")
    @Permission("lumaguilds.command.claim.untrust")
    @PlayerOnly
    fun untrust(
        @Context player: Player,
        @Arg(name = "player") @Suggests("claim.players.untrust") target: ClaimArgumentToken,
        @Arg(name = "permission") @Suggests("claim.permissions.untrust") permission: ClaimArgumentToken,
    ) {
        val resolved = resolveClaimPlayer(player, target, lang) ?: return
        untrust(player, resolved, permission.value)
    }

    fun untrust(
        player: Player,
        target: Player,
        permissionName: String,
    ) {
        val permission = parseClaimPermission(permissionName)
        if (permission == null || !target.isOnline) {
            player.sendMessage(lang.msg("general.error"))
            return
        }
        val partition = access.partitionAtPlayer(player) ?: return
        if (!access.hasPermission(player, partition, RankPermission.MANAGE_PERMISSIONS)) return

        val claimId = partition.claimId
        val targetName = target.name
        val message = when (revokePlayerClaimPermission.execute(claimId, target.uniqueId, permission)) {
            is RevokePlayerClaimPermissionResult.Success -> lang.msg(
                "command.claim.untrust.success",
                "permission" to playerPermissionName(lang, permission),
                "player" to targetName,
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
            )
            is RevokePlayerClaimPermissionResult.DoesNotExist -> lang.msg(
                "command.claim.untrust.does_not_exist",
                "player" to targetName,
                "permission" to playerPermissionName(lang, permission),
                "claim" to (getClaimDetails.execute(claimId)?.name ?: lang.plain("general.name_error")),
            )
            is RevokePlayerClaimPermissionResult.ClaimNotFound -> lang.msg("command.common.unknown_claim")
            is RevokePlayerClaimPermissionResult.StorageError -> lang.msg("general.error")
        }
        player.sendMessage(message)
    }
}

/** Reuses the declared finite-enum lookup so the dynamic locale baseline is unchanged. */
private fun playerPermissionName(lang: LangService, permission: ClaimPermission): String =
    PlainTextComponentSerializer.plainText()
        .serialize(lang.localizedClaimPermissionNames(listOf(permission)).single())
