package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import java.util.Locale

internal fun sortMembersByRank(
    members: Collection<Member>,
    ranks: Collection<Rank>,
    nameOf: (Member) -> String,
): List<Member> {
    val priorityByRank = ranks.associate { rank -> rank.id to rank.priority }

    return members.sortedWith(
        compareBy<Member> { member -> priorityByRank[member.rankId] ?: Int.MAX_VALUE }
            .thenBy { member -> nameOf(member).lowercase(Locale.ROOT) }
            .thenBy { member -> member.playerId }
    )
}
