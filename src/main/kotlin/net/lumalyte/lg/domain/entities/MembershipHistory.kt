package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

enum class DepartureReason { LEFT, KICKED, DISBANDED }

data class MembershipHistory(
    val id: UUID,
    val playerId: UUID,
    val guildId: UUID,
    val joinedAt: Instant,
    val departedAt: Instant? = null,
    val departureReason: DepartureReason? = null
) {
    val isOpen: Boolean get() = departedAt == null
}
