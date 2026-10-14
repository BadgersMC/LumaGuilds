package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/** Existing member managers retain invite access; dedicated recruiters gain no member mutation rights. */
object GuildRecruitmentPermissions {
    fun canInvite(members: MemberService, actor: UUID, guild: UUID): Boolean =
        members.hasPermission(actor, guild, RankPermission.MANAGE_RECRUITMENT) ||
            members.hasPermission(actor, guild, RankPermission.MANAGE_MEMBERS)

    fun canReview(members: MemberService, actor: UUID, guild: UUID): Boolean =
        members.hasPermission(actor, guild, RankPermission.MANAGE_RECRUITMENT)

    fun canEditSettings(members: MemberService, actor: UUID, guild: UUID): Boolean =
        members.hasPermission(actor, guild, RankPermission.EDIT_RECRUITMENT_SETTINGS)
}
