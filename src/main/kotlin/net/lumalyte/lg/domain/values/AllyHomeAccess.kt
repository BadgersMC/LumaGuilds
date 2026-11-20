package net.lumalyte.lg.domain.values

import java.util.UUID

/**
 * REQ-182: a host guild's own rules for its allied homes, on top of which allies
 * may visit. [visitorRanks] maps an allied guild to the ranks of that guild the
 * host admits; an ally with no entry has every rank admitted. [blockedPlayers]
 * are refused whatever their rank. Visitors still need "Use ally homes" in their
 * own guild; that check stays with the guild service.
 */
/**
 * REQ-183: the visiting guild's own pick of its ranks for one ally. With no pick
 * the rank's Use ally homes permission decides; the owner always keeps access.
 */
object AllyHomeOutbound {
    fun allows(pick: Set<UUID>?, rankId: UUID, isOwner: Boolean, hasUsePermission: Boolean): Boolean =
        isOwner || (pick?.contains(rankId) ?: hasUsePermission)
}

data class AllyHomeAccess(
    val visitorRanks: Map<UUID, Set<UUID>> = emptyMap(),
    val blockedPlayers: Set<UUID> = emptySet(),
) {
    fun admits(visitorGuildId: UUID, visitorRankId: UUID, playerId: UUID): Boolean =
        playerId !in blockedPlayers && (visitorRanks[visitorGuildId]?.contains(visitorRankId) ?: true)
}
