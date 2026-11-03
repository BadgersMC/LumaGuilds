package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.application.services.MemberService
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * Administrative orphan recovery. All invocations and cache updates run on the command thread.
 * Parsing, confirmation, messages, and audit helpers share this one command-thread state machine.
 * Expression bodies follow FunctionSignature; MultilineExpressionWrapping requires conflicting placement.
 */
@Suppress("TooManyFunctions", "MultilineExpressionWrapping")
internal class GuildOwnerRecoveryCommand(
    private val members: MemberService,
    private val lang: LangService,
    private val now: () -> Instant = Instant::now,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
    private val audit: (String) -> Unit = { Logger.getLogger("LumaGuilds").info(it) },
) {
    private val pending = mutableMapOf<String, PendingRecovery>()

    fun execute(sender: CommandSender, args: Array<out String>) {
        val actor = authorize(sender) ?: return
        pending.entries.removeIf { !now().isBefore(it.value.expiresAt) }
        val request = parseRequest(args)
        if (request == null) {
            sender.sendMessage(lang.msg("admin.owner_recovery.usage"))
            return
        }
        if (request.token == null) preview(sender, actor, request) else confirm(sender, actor, request)
    }

    private fun authorize(sender: CommandSender): String? {
        val actor = actorKey(sender)
        if (actor == null) {
            sender.sendMessage(lang.msg("admin.owner_recovery.sender_required"))
            return null
        }
        return if (!sender.hasPermission(BASE_PERMISSION) || !sender.hasPermission(PERMISSION)) {
            pending.remove(actor)
            sender.sendMessage(lang.msg("admin.common.no_permission"))
            null
        } else {
            actor
        }
    }

    private fun parseRequest(args: Array<out String>): Request? {
        if (!validRequestShape(args)) return null
        val guildId = parseUuid(args[GUILD_ARGUMENT])
        val playerId = parseUuid(args[MEMBER_ARGUMENT])
        return if (guildId == null || playerId == null) {
            null
        } else {
            Request(guildId, playerId, args.getOrNull(TOKEN_ARGUMENT))
        }
    }

    private fun validRequestShape(args: Array<out String>): Boolean {
        val validSize =
            when (args.size) {
                PREVIEW_ARGUMENTS -> true
                CONFIRM_ARGUMENTS -> args[CONFIRM_ARGUMENT].equals("confirm", true)
                else -> false
            }
        return validSize && args[0].equals("owner", true) && args[1].equals("recover", true)
    }

    private fun preview(sender: CommandSender, actor: String, request: Request) {
        pending.remove(actor)
        val result =
            attemptStorage(sender, actor, request) {
                members.previewOwnerRecovery(request.guildId, request.playerId)
            } ?: return
        when (result) {
            is OwnerRecoveryPreviewResult.Rejected -> reportPreviewRejection(sender, actor, request, result.reason)
            is OwnerRecoveryPreviewResult.Ready -> offerConfirmation(sender, actor, result.preview)
        }
    }

    private fun reportPreviewRejection(
        sender: CommandSender,
        actor: String,
        request: Request,
        reason: OwnerRecoveryRejection,
    ) {
        auditRequest(actor, request, "PREVIEW_REJECTED_$reason")
        reject(sender, reason)
    }

    private fun offerConfirmation(sender: CommandSender, actor: String, preview: OwnerRecoveryPreview) {
        val confirmation = PendingRecovery(preview, newToken(), now().plusSeconds(CONFIRM_SECONDS))
        pending[actor] = confirmation
        auditPreview(actor, preview, "PREVIEW")
        showPreview(sender, preview)
        showConfirmation(sender, confirmation)
    }

    private fun confirm(sender: CommandSender, actor: String, request: Request) {
        val confirmation = pending[actor]?.takeIf { it.matches(request) }
        if (confirmation == null) {
            auditRequest(actor, request, "CONFIRMATION_REJECTED")
            sender.sendMessage(lang.msg("admin.owner_recovery.invalid_confirmation"))
            return
        }
        // Consume before attempting storage. Even an uncertain outcome requires a new preview.
        pending.remove(actor)
        val result =
            attemptStorage(sender, actor, request) {
                members.recoverOwnership(confirmation.preview)
            } ?: return
        reportRecovery(sender, actor, confirmation.preview, result)
    }

    // This is the final command boundary: adapters can wrap SQL errors in unchecked exceptions.
    // Report an uncertain outcome and require a new preview for every nonfatal storage exception.
    @Suppress("TooGenericExceptionCaught")
    private fun <T : Any> attemptStorage(
        sender: CommandSender,
        actor: String,
        request: Request,
        operation: () -> T,
    ): T? = try {
        operation()
    } catch (error: Exception) {
        recordFailure(actor, request, error)
        reject(sender, OwnerRecoveryRejection.STORAGE_FAILURE)
        null
    }

    private fun reportRecovery(
        sender: CommandSender,
        actor: String,
        preview: OwnerRecoveryPreview,
        result: OwnerRecoveryResult,
    ) {
        when (result) {
            is OwnerRecoveryResult.Rejected -> {
                auditPreview(actor, preview, "REJECTED_${result.reason}")
                reject(sender, result.reason)
            }
            is OwnerRecoveryResult.Recovered -> {
                auditPreview(actor, result.preview, "RECOVERED")
                showSuccess(sender, result.preview)
            }
        }
    }

    // LocaleSourceScanner requires literal placeholder names to verify translation contracts.
    @Suppress("StringLiteralDuplication")
    private fun showPreview(sender: CommandSender, preview: OwnerRecoveryPreview) {
        sender.sendMessage(
            lang.msg(
                "admin.owner_recovery.preview",
                "guild" to preview.guildName,
                "guild_id" to preview.guildId.toString(),
                "member_id" to preview.member.playerId.toString(),
                "current_rank" to preview.currentRankName,
                "current_rank_id" to preview.member.rankId.toString(),
                "owner_rank" to preview.ownerRankName,
                "owner_rank_id" to preview.ownerRankId.toString(),
            ),
        )
    }

    // Keep literal placeholders visible to LocaleSourceScanner, as in showPreview.
    @Suppress("StringLiteralDuplication")
    private fun showConfirmation(sender: CommandSender, confirmation: PendingRecovery) {
        sender.sendMessage(
            lang.msg(
                "admin.owner_recovery.confirm",
                "guild_id" to confirmation.preview.guildId.toString(),
                "member_id" to confirmation.preview.member.playerId.toString(),
                "token" to confirmation.token,
            ),
        )
    }

    // Keep literal placeholders visible to LocaleSourceScanner, as in showPreview.
    @Suppress("StringLiteralDuplication")
    private fun showSuccess(sender: CommandSender, preview: OwnerRecoveryPreview) {
        sender.sendMessage(
            lang.msg(
                "admin.owner_recovery.success",
                "guild" to preview.guildName,
                "guild_id" to preview.guildId.toString(),
                "member_id" to preview.member.playerId.toString(),
            ),
        )
    }

    // Keep this exhaustive so every added rejection requires a translated response.
    private fun reject(sender: CommandSender, reason: OwnerRecoveryRejection) {
        val message =
            when (reason) {
                OwnerRecoveryRejection.GUILD_NOT_FOUND -> lang.msg("admin.owner_recovery.rejected.guild_not_found")
                OwnerRecoveryRejection.MEMBER_NOT_FOUND -> lang.msg("admin.owner_recovery.rejected.member_not_found")
                OwnerRecoveryRejection.INVALID_OWNER_RANKS ->
                    lang.msg("admin.owner_recovery.rejected.invalid_owner_ranks")
                OwnerRecoveryRejection.INVALID_MEMBER_RANKS ->
                    lang.msg("admin.owner_recovery.rejected.invalid_member_ranks")
                OwnerRecoveryRejection.ALREADY_OWNED -> lang.msg("admin.owner_recovery.rejected.already_owned")
                OwnerRecoveryRejection.STALE_PREVIEW -> lang.msg("admin.owner_recovery.rejected.stale_preview")
                OwnerRecoveryRejection.STORAGE_FAILURE -> lang.msg("admin.owner_recovery.rejected.storage_failure")
                OwnerRecoveryRejection.UNSUPPORTED_REPOSITORY ->
                    lang.msg("admin.owner_recovery.rejected.unsupported_repository")
            }
        sender.sendMessage(message)
    }

    private fun auditRequest(actor: String, request: Request, outcome: String) {
        audit("OWNER_RECOVERY actor=$actor guild=${request.guildId} member=${request.playerId} outcome=$outcome")
    }

    private fun auditPreview(actor: String, preview: OwnerRecoveryPreview, outcome: String) {
        audit(
            "OWNER_RECOVERY actor=$actor guild=${preview.guildId} member=${preview.member.playerId} " +
                "oldRank=${preview.member.rankId} ownerRank=${preview.ownerRankId} outcome=$outcome",
        )
    }

    private fun recordFailure(actor: String, request: Request, error: Exception) {
        auditRequest(actor, request, "STORAGE_FAILURE_${error.javaClass.simpleName}")
    }

    private fun actorKey(sender: CommandSender): String? = when (sender) {
        is Player -> "PLAYER:${sender.uniqueId}"
        is ConsoleCommandSender -> "CONSOLE"
        else -> null
    }

    private fun parseUuid(value: String): UUID? =
        runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { it.toString().equals(value, true) }

    private data class Request(val guildId: UUID, val playerId: UUID, val token: String?)

    private class PendingRecovery(
        val preview: OwnerRecoveryPreview,
        val token: String,
        val expiresAt: Instant,
    ) {
        fun matches(request: Request): Boolean =
            token == request.token && preview.guildId == request.guildId && preview.member.playerId == request.playerId
    }

    companion object {
        const val BASE_PERMISSION = "lumaguilds.admin"
        const val PERMISSION = "lumaguilds.admin.owner.recover"
        private const val GUILD_ARGUMENT = 2
        private const val MEMBER_ARGUMENT = 3
        private const val CONFIRM_ARGUMENT = 4
        private const val TOKEN_ARGUMENT = 5
        private const val PREVIEW_ARGUMENTS = 4
        private const val CONFIRM_ARGUMENTS = 6
        private const val CONFIRM_SECONDS = 60L
    }
}
