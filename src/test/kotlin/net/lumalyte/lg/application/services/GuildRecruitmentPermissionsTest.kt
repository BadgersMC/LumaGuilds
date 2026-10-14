package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class GuildRecruitmentPermissionsTest {
    private val actor = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private fun members(vararg granted: RankPermission): MemberService = mockk<MemberService>().also { service ->
        every { service.hasPermission(actor, guild, any()) } answers { thirdArg<RankPermission>() in granted }
    }

    @Test fun `dedicated recruiter can invite and review but cannot edit settings`() {
        val members = members(RankPermission.MANAGE_RECRUITMENT)
        assertTrue(GuildRecruitmentPermissions.canInvite(members, actor, guild))
        assertTrue(GuildRecruitmentPermissions.canReview(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canEditSettings(members, actor, guild))
        assertFalse(members.hasPermission(actor, guild, RankPermission.MANAGE_MEMBERS))
        assertFalse(members.hasPermission(actor, guild, RankPermission.MANAGE_RANKS))
    }

    @Test fun `existing member manager retains invitations without receiving new recruitment rights`() {
        val members = members(RankPermission.MANAGE_MEMBERS)
        assertTrue(GuildRecruitmentPermissions.canInvite(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canReview(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canEditSettings(members, actor, guild))
    }

    @Test fun `settings editor cannot invite or review without recruiter permission`() {
        val members = members(RankPermission.EDIT_RECRUITMENT_SETTINGS)
        assertTrue(GuildRecruitmentPermissions.canEditSettings(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canInvite(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canReview(members, actor, guild))
    }

    @Test fun `ordinary member has neither right`() {
        val members = members()
        assertFalse(GuildRecruitmentPermissions.canInvite(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canReview(members, actor, guild))
        assertFalse(GuildRecruitmentPermissions.canEditSettings(members, actor, guild))
    }
}
