package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-193: which member-page buttons work, matching MemberServiceBukkit's rules. */
class MemberActionsTest {
    private val ranks = listOf(RankStep("Leader", 0), RankStep("Co-Leader", 1), RankStep("Officer", 2), RankStep("Member", 5), RankStep("Recruit", 9))

    private fun of(viewer: Int?, target: Int, manage: Boolean = true, self: Boolean = false) =
        MemberActions.of(manage, self, viewer, target, ranks)

    @Test
    fun `officer can demote, kick and ban a member but not promote them to officer`() {
        val a = of(viewer = 2, target = 5)
        assertEquals(MemberAction.Denied(MemberActionDenial.TOP_RANK_FOR_VIEWER, "Officer"), a.promote)
        assertEquals(MemberAction.Allowed("Recruit"), a.demote)
        assertEquals(MemberAction.Allowed(), a.kick)
        assertEquals(MemberAction.Allowed(), a.ban)
    }

    @Test
    fun `officer can promote a recruit to member`() {
        assertEquals(MemberAction.Allowed("Member"), of(viewer = 2, target = 9).promote)
        assertEquals(MemberAction.Denied(MemberActionDenial.LOWEST_RANK), of(viewer = 2, target = 9).demote)
    }

    @Test
    fun `nobody acts on an equal or higher rank`() {
        val a = of(viewer = 2, target = 2)
        listOf(a.promote, a.demote, a.kick, a.ban).forEach {
            assertEquals(MemberActionDenial.NOT_OUTRANKED, (it as MemberAction.Denied).reason)
        }
        assertEquals(MemberActionDenial.NOT_OUTRANKED, (of(viewer = 5, target = 0).kick as MemberAction.Denied).reason)
    }

    @Test
    fun `the leader cannot promote anyone to leader`() {
        assertEquals(MemberAction.Denied(MemberActionDenial.TOP_RANK_FOR_VIEWER, "Leader"), of(viewer = 0, target = 1).promote)
        assertEquals(MemberAction.Allowed("Co-Leader"), of(viewer = 0, target = 2).promote)
    }

    @Test
    fun `no permission, not a member, or yourself`() {
        assertEquals(MemberActionDenial.NO_PERMISSION, (of(viewer = 0, target = 5, manage = false).ban as MemberAction.Denied).reason)
        assertEquals(MemberActionDenial.NO_PERMISSION, (of(viewer = null, target = 5).kick as MemberAction.Denied).reason)
        assertEquals(MemberActionDenial.SELF, (of(viewer = 2, target = 2, self = true).kick as MemberAction.Denied).reason)
    }
}
