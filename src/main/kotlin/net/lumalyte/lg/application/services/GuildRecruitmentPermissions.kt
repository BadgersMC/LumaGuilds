package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/** Existing member managers retain invite access; dedicated recruiters gain no member mutation rights. */
internal object GuildRecruitmentPermissions {
    /** Grants invites through either dedicated recruitment authority or the legacy member-manager permission. */
    fun canInvite(members: MemberService, actor: UUID, guild: UUID): Boolean =
        members.hasPermission(actor, guild, RankPermission.MANAGE_RECRUITMENT) ||
            members.hasPermission(actor, guild, RankPermission.MANAGE_MEMBERS)

    /** Reads and decides private applications only for ranks granted recruitment authority. */
    fun canReview(members: MemberService, actor: UUID, guild: UUID): Boolean =
        members.hasPermission(actor, guild, RankPermission.MANAGE_RECRUITMENT)

    /** Listing edits use a distinct permission from application review. */
    fun canEditSettings(members: MemberService, actor: UUID, guild: UUID): Boolean =
        members.hasPermission(actor, guild, RankPermission.EDIT_RECRUITMENT_SETTINGS)
}
