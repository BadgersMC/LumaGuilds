package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import org.bukkit.entity.Player
import java.security.MessageDigest
import java.util.UUID

/** Private application digests retain authorization and durable replay protection. */
internal class GuildRecruitmentNotifications(
    private val directory: RecruitmentDirectoryRepository,
    private val recruitment: GuildRecruitmentService,
    private val guilds: GuildService,
    private val members: MemberService,
    private val lang: LangService,
) {
    fun deliver(player: Player) {
        if (player.hasPermission(RECRUITMENT_PERMISSION)) {
            val own = recruitment.mine(player.uniqueId)
            val inbox = inbox(player.uniqueId)
            val records = (own + inbox).distinctBy { it.id }.sortedBy { it.id.toString() }
            if (records.isNotEmpty() && directory.consumeNotification(player.uniqueId, signature(records))) {
                player.sendMessage(
                    lang.msg(
                        "recruitment.discovery.updated",
                        "offers" to own.count { it.status == ApplicationStatus.OFFERED },
                        "pending" to inbox.count { it.status == ApplicationStatus.SUBMITTED },
                    ).clickEvent(ClickEvent.runCommand(RECRUITMENT_COMMAND)),
                )
            }
        }
    }

    private fun inbox(player: UUID): List<GuildApplication> {
        return guilds.getPlayerGuilds(player)
            .filter { GuildRecruitmentPermissions.canReview(members, player, it.id) }
            .flatMap { recruitment.inbox(player, it.id) }
    }

    private fun signature(records: List<GuildApplication>): String {
        val text = records.joinToString(";") { "${it.id}:${it.status}" }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
