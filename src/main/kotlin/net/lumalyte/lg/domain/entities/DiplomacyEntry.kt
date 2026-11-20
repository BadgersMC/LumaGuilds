package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/** REQ-203: a diplomacy change as one guild saw it. */
enum class DiplomacyKind {
    ALLIED, ALLIANCE_ENDED, DECLARED_ENEMIES, DECLARED_ENEMIES_BY, TRUCE_STARTED, TRUCE_ENDED, PEACE, WAR_WON, WAR_LOST, WAR_DRAWN,
}

/** REQ-203: one history entry. [days] is set for truces. */
data class DiplomacyEntry(
    val guildId: UUID,
    val otherGuildId: UUID,
    val kind: DiplomacyKind,
    val actorId: UUID?,
    val at: Instant,
    val days: Int? = null,
)
