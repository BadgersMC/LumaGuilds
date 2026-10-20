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

/** Administrative orphan recovery. All invocations and cache updates run on the command thread. */
internal class GuildOwnerRecoveryCommand(
    private val members: MemberService,
    private val lang: LangService,
    private val now: () -> Instant = Instant::now,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
    private val audit: (String) -> Unit = { Logger.getLogger("LumaGuilds").info(it) },
) {
    private val pending = mutableMapOf<String, PendingRecovery>()

    fun execute(sender: CommandSender, args: Array<out String>) {
        val actor = actorKey(sender)
        if (actor == null) {
            sender.sendMessage(lang.msg("admin.owner_recovery.sender_required"))
            return
        }
        if (!sender.hasPermission(BASE_PERMISSION) || !sender.hasPermission(PERMISSION)) {
            pending.remove(actor)
            sender.sendMessage(lang.msg("admin.common.no_permission"))
            return
        }

        pending.entries.removeIf { !now().isBefore(it.value.expiresAt) }
        val request = parseRequest(args)
        if (request == null) {
            sender.sendMessage(lang.msg("admin.owner_recovery.usage"))
            return
        }
        if (request.token == null) preview(sender, actor, request) else confirm(sender, actor, request)
    }

    private fun parseRequest(args: Array<out String>): Request? {
        if (args.size != PREVIEW_ARGUMENTS && args.size != CONFIRM_ARGUMENTS) return null
        if (!args[0].equals("owner", true) || !args[1].equals("recover", true)) return null
        val guildId = parseUuid(args[2]) ?: return null
        val playerId = parseUuid(args[3]) ?: return null
        val token = if (args.size == CONFIRM_ARGUMENTS) {
            if (!args[4].equals("confirm", true)) return null
            args[5]
        } else {
            null
        }
        return Request(guildId, playerId, token)
    }

    private fun preview(sender: CommandSender, actor: String, request: Request) {
        pending.remove(actor)
        val result = try {
            members.previewOwnerRecovery(request.guildId, request.playerId)
        } catch (error: Exception) {
            recordFailure(actor, request, error)
            reject(sender, OwnerRecoveryRejection.STORAGE_FAILURE)
            return
        }
        when (result) {
            is OwnerRecoveryPreviewResult.Rejected -> {
                auditRequest(actor, request, "PREVIEW_REJECTED_${result.reason}")
                reject(sender, result.reason)
            }
            is OwnerRecoveryPreviewResult.Ready -> {
                val preview = result.preview
                val confirmation = PendingRecovery(preview, newToken(), now().plusSeconds(CONFIRM_SECONDS))
                pending[actor] = confirmation
                auditPreview(actor, preview, "PREVIEW")
                sender.sendMessage(lang.msg(
                    "admin.owner_recovery.preview",
                    "guild" to preview.guildName,
                    "guild_id" to preview.guildId.toString(),
                    "member_id" to preview.member.playerId.toString(),
                    "current_rank" to preview.currentRankName,
                    "current_rank_id" to preview.member.rankId.toString(),
                    "owner_rank" to preview.ownerRankName,
                    "owner_rank_id" to preview.ownerRankId.toString(),
                ))
                sender.sendMessage(lang.msg(
                    "admin.owner_recovery.confirm",
                    "guild_id" to preview.guildId.toString(),
                    "member_id" to preview.member.playerId.toString(),
                    "token" to confirmation.token,
                ))
            }
        }
    }

    private fun confirm(sender: CommandSender, actor: String, request: Request) {
        val confirmation = pending[actor]
        if (confirmation == null || confirmation.token != request.token ||
            confirmation.preview.guildId != request.guildId ||
            confirmation.preview.member.playerId != request.playerId
        ) {
            auditRequest(actor, request, "CONFIRMATION_REJECTED")
            sender.sendMessage(lang.msg("admin.owner_recovery.invalid_confirmation"))
            return
        }
        // Consume before attempting storage. Even an uncertain outcome requires a new preview.
        pending.remove(actor)
        val result = try {
            members.recoverOwnership(confirmation.preview)
        } catch (error: Exception) {
            recordFailure(actor, request, error)
            reject(sender, OwnerRecoveryRejection.STORAGE_FAILURE)
            return
        }
        when (result) {
            is OwnerRecoveryResult.Rejected -> {
                auditPreview(actor, confirmation.preview, "REJECTED_${result.reason}")
                reject(sender, result.reason)
            }
            is OwnerRecoveryResult.Recovered -> {
                auditPreview(actor, result.preview, "RECOVERED")
                sender.sendMessage(lang.msg(
                    "admin.owner_recovery.success",
                    "guild" to result.preview.guildName,
                    "guild_id" to result.preview.guildId.toString(),
                    "member_id" to result.preview.member.playerId.toString(),
                ))
            }
        }
    }

    private fun reject(sender: CommandSender, reason: OwnerRecoveryRejection) {
        val message = when (reason) {
            OwnerRecoveryRejection.GUILD_NOT_FOUND -> lang.msg("admin.owner_recovery.rejected.guild_not_found")
            OwnerRecoveryRejection.MEMBER_NOT_FOUND -> lang.msg("admin.owner_recovery.rejected.member_not_found")
            OwnerRecoveryRejection.INVALID_OWNER_RANKS -> lang.msg("admin.owner_recovery.rejected.invalid_owner_ranks")
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

    private data class PendingRecovery(
        val preview: OwnerRecoveryPreview,
        val token: String,
        val expiresAt: Instant,
    )

    companion object {
        const val BASE_PERMISSION = "lumaguilds.admin"
        const val PERMISSION = "lumaguilds.admin.owner.recover"
        private const val PREVIEW_ARGUMENTS = 4
        private const val CONFIRM_ARGUMENTS = 6
        private const val CONFIRM_SECONDS = 60L
    }
}
