package net.lumalyte.lg.domain.values

import java.util.UUID

/** REQ-222: who receives a party message. Hiding party chat never hides your own message. */
object PartyRecipients {
    fun of(onlineMembers: Set<UUID>, sender: UUID, visible: (UUID) -> Boolean): Set<UUID> =
        onlineMembers.filterTo(mutableSetOf()) { it == sender || visible(it) }
}
