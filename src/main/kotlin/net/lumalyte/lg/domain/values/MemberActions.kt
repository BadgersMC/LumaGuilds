package net.lumalyte.lg.domain.values

/** REQ-193: why a member-page button is greyed out. */
enum class MemberActionDenial { NO_PERMISSION, SELF, NOT_OUTRANKED, TOP_RANK_FOR_VIEWER, LOWEST_RANK }

/** REQ-193: one member-page button. [rankName] is the rank a promote or demote moves to. */
sealed interface MemberAction {
    data class Allowed(val rankName: String? = null) : MemberAction
    data class Denied(val reason: MemberActionDenial, val rankName: String? = null) : MemberAction
}

/** REQ-193: the four member-page buttons. */
data class MemberActionSet(
    val promote: MemberAction,
    val demote: MemberAction,
    val kick: MemberAction,
    val ban: MemberAction,
)

/** A guild rank as the member page needs it. Lower [priority] is a higher rank; 0 is the owner. */
data class RankStep(val name: String, val priority: Int)

/**
 * REQ-193: which member-page buttons work. Mirrors MemberServiceBukkit: the viewer needs Manage
 * Members and must outrank the target; promote moves one rank up and must stay below the viewer
 * and never reach owner; demote moves one rank down.
 */
object MemberActions {
    fun of(
        canManage: Boolean,
        self: Boolean,
        viewerPriority: Int?,
        targetPriority: Int,
        ranks: List<RankStep>,
    ): MemberActionSet {
        val gate = when {
            !canManage || viewerPriority == null -> MemberActionDenial.NO_PERMISSION
            self -> MemberActionDenial.SELF
            viewerPriority >= targetPriority -> MemberActionDenial.NOT_OUTRANKED
            else -> null
        }
        val ordered = ranks.sortedBy { it.priority }
        val index = ordered.indexOfFirst { it.priority == targetPriority }
        val up = ordered.getOrNull(index - 1).takeIf { index > 0 }
        val down = ordered.getOrNull(index + 1).takeIf { index >= 0 }

        val promote = when {
            gate != null -> MemberAction.Denied(gate, up?.name)
            up == null -> MemberAction.Denied(MemberActionDenial.TOP_RANK_FOR_VIEWER)
            up.priority == 0 || up.priority <= viewerPriority!! -> MemberAction.Denied(MemberActionDenial.TOP_RANK_FOR_VIEWER, up.name)
            else -> MemberAction.Allowed(up.name)
        }
        val demote = when {
            gate != null -> MemberAction.Denied(gate, down?.name)
            down == null -> MemberAction.Denied(MemberActionDenial.LOWEST_RANK)
            else -> MemberAction.Allowed(down.name)
        }
        val remove = gate?.let { MemberAction.Denied(it) } ?: MemberAction.Allowed()
        return MemberActionSet(promote, demote, remove, remove)
    }
}
